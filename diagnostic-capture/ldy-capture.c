#define _GNU_SOURCE
#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifdef __ANDROID__
#include <sys/system_properties.h>
#endif

/* Fixed diagnostic budget: first 2 MiB + four 2 MiB tail files; 180 seconds. */
#define SEGMENT (2U * 1024U * 1024U)
#define TAILS 4U
#define LIFETIME 180
#define SYNC_BYTES (256U * 1024U)
#define CAPTURE_ROOT "ldy-guest-capture"
#define FREE_RESERVE (8ULL * 1024ULL * 1024ULL)
#define METADATA_BUDGET (256ULL * 1024ULL)
#define CAPTURE_BUDGET ((1ULL + TAILS) * SEGMENT + 4096ULL)
static volatile sig_atomic_t stopped;
static void stop_signal(int signal_number) { (void)signal_number; stopped = 1; }
static int64_t now_ms(void) {
    struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t)) return -1;
    return (int64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}
static int write_all(int fd, const void *buffer, size_t size) {
    const char *p = buffer;
    while (size) {
        ssize_t n = write(fd, p, size);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        p += n; size -= (size_t)n;
    }
    return 0;
}
static bool option(const char *options, const char *wanted) {
    size_t n = strlen(wanted);
    for (const char *p = options; *p;) {
        const char *end = strchr(p, ',');
        size_t size = end ? (size_t)(end - p) : strlen(p);
        if (size == n && !memcmp(p, wanted, n)) return true;
        if (!end) break;
        p = end + 1;
    }
    return false;
}
/* Reject bind-subdirectories, wrong devices, read-only mounts and other filesystems. */
static bool mount_matches(const char *line, unsigned device_major, unsigned device_minor, uint64_t expected_mount) {
    unsigned a, b;
    unsigned long long mount_id;
    char root[256], mountpoint[256], options[256], fs[32], source[256], super[256];
    const char *separator = strstr(line, " - ");
    if (!separator || sscanf(line, "%llu %*u %u:%u %255s %255s %255s",
                             &mount_id, &a, &b, root, mountpoint, options) != 6 ||
        sscanf(separator + 3, "%31s %255s %255s", fs, source, super) != 3) return false;
    return expected_mount && mount_id == expected_mount && a == device_major && b == device_minor && !strcmp(root, "/") &&
        !strcmp(mountpoint, "/cache") && !strcmp(fs, "ext4") &&
        option(options, "rw") && option(super, "rw");
}
static int verified_cache(void) {
    struct stat block, mounted;
    struct statfs fs;
    struct statx identity;
    int fd = open("/cache", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) return -1;
    if (statx(fd, "", AT_EMPTY_PATH, STATX_MNT_ID, &identity) ||
        !(identity.stx_mask & STATX_MNT_ID) || !identity.stx_mnt_id ||
        stat("/dev/block/by-name/cache", &block) || !S_ISBLK(block.st_mode) ||
        fstat(fd, &mounted) || mounted.st_dev != block.st_rdev ||
        fstatfs(fd, &fs) || (unsigned long)fs.f_type != 0xef53UL) goto bad;
    FILE *mounts = fopen("/proc/self/mountinfo", "re");
    if (!mounts) goto bad;
    char *line = NULL;
    size_t size = 0;
    bool found = false;
    while (getline(&line, &size, mounts) >= 0) {
        if (mount_matches(line, major(block.st_rdev), minor(block.st_rdev), identity.stx_mnt_id)) found = true;
    }
    free(line);
    fclose(mounts);
    if (found) return fd;
bad:
    close(fd);
    return -1;
}
/* Mount IDs reject descendant bind mounts even when st_dev is unchanged. */
static bool mount_identity_equal(const struct statx *a, const struct statx *b) {
    return (a->stx_mask & STATX_MNT_ID) && (b->stx_mask & STATX_MNT_ID) &&
        a->stx_mnt_id && a->stx_mnt_id == b->stx_mnt_id;
}
static bool same_mount(int a, int b) {
    struct statx left, right;
    return !statx(a, "", AT_EMPTY_PATH, STATX_MNT_ID, &left) &&
        !statx(b, "", AT_EMPTY_PATH, STATX_MNT_ID, &right) &&
        mount_identity_equal(&left, &right);
}
static bool space_sufficient(const struct statvfs *fs, uint64_t required) {
    uint64_t unit = fs->f_frsize, blocks = fs->f_bavail;
    return unit && blocks <= UINT64_MAX / unit && blocks * unit >= required;
}
static int require_space(int fd, uint64_t required) {
    struct statvfs fs;
    if (fstatvfs(fd, &fs)) return -1;
    if (!space_sufficient(&fs, required)) { errno = ENOSPC; return -1; }
    return 0;
}
static int checked_directory(int parent, const char *name, bool exclusive) {
    if (mkdirat(parent, name, 0700) && (errno != EEXIST || exclusive)) return -1;
    int fd = openat(parent, name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    struct stat st;
    if (fd < 0) return -1;
    if (!same_mount(parent, fd) || fstat(fd, &st) || st.st_uid != geteuid() || (st.st_mode & 0777) != 0700) {
        close(fd); return -1;
    }
    if (fsync(fd) || fsync(parent)) { close(fd); return -1; }
    return fd;
}
/* Preserve old evidence; refuse a fifth session rather than deleting it. */
static bool session_capacity(int root) {
    int copy = openat(root, ".", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (copy < 0) return false;
    DIR *directory = fdopendir(copy);
    if (!directory) { close(copy); return false; }
    unsigned entries = 0;
    struct dirent *entry;
    errno = 0;
    while ((entry = readdir(directory)))
        if (strcmp(entry->d_name, ".") && strcmp(entry->d_name, "..")) ++entries;
    int error = errno;
    closedir(directory);
    return !error && entries < 4;
}
static int fresh_file(int parent, const char *name) {
    return openat(parent, name, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
}
struct sink {
    int directory, first, tail, status;
    unsigned tail_index;
    size_t first_bytes, tail_bytes, since_sync, status_bytes;
    int64_t last_sync;
};
static int note(struct sink *s, const char *text) {
    size_t size = strlen(text);
    if (size > 4096 - s->status_bytes) return -1;
    if (write_all(s->status, text, size)) return -1;
    s->status_bytes += size;
    return 0;
}
static int sync_sink(struct sink *s) {
    if (fdatasync(s->first) || (s->tail >= 0 && fdatasync(s->tail)) ||
        fdatasync(s->status) || fsync(s->directory)) {
        int saved = errno;
        char message[80];
        snprintf(message, sizeof(message), "sync_error=%d\n", saved);
        (void)note(s, message);
        (void)fdatasync(s->status);
        errno = saved;
        return -1;
    }
    s->since_sync = 0; s->last_sync = now_ms();
    return 0;
}
static int next_tail(struct sink *s) {
    if (s->tail >= 0) {
        int synced = fdatasync(s->tail);
        int closed = close(s->tail);
        if (synced || closed) { s->tail = -1; return -1; }
        s->tail = -1;
        s->tail_index = (s->tail_index + 1) % TAILS;
    }
    char name[24];
    snprintf(name, sizeof(name), "tail-%u.log", s->tail_index);
    int fd = openat(s->directory, name, O_WRONLY | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0600);
    struct stat st;
    if (fd < 0) return -1;
    if (!same_mount(s->directory, fd) || fstat(fd, &st) || !S_ISREG(st.st_mode) || st.st_uid != geteuid() ||
        st.st_nlink != 1 || (st.st_mode & 0777) != 0600 || ftruncate(fd, 0)) {
        close(fd); return -1;
    }
    s->tail = fd; s->tail_bytes = 0;
    char message[64];
    snprintf(message, sizeof(message), "tail_open=%u\n", s->tail_index);
    if (s->status_bytes > 3800) { errno = ENOSPC; return -1; }
    return note(s, message) || fsync(s->directory) ? -1 : 0;
}
static int sink_write(struct sink *s, const char *data, size_t bytes) {
    while (bytes) {
        bool initial = s->first_bytes < SEGMENT;
        if (!initial && (s->tail < 0 || s->tail_bytes == SEGMENT) && next_tail(s)) return -1;
        size_t available = SEGMENT - (initial ? s->first_bytes : s->tail_bytes);
        size_t n = bytes < available ? bytes : available;
        if (require_space(s->directory, FREE_RESERVE + METADATA_BUDGET + n) ||
            write_all(initial ? s->first : s->tail, data, n)) return -1;
        if (initial) s->first_bytes += n; else s->tail_bytes += n;
        s->since_sync += n; data += n; bytes -= n;
    }
    if (s->since_sync >= SYNC_BYTES || now_ms() - s->last_sync >= 1000) return sync_sink(s);
    return 0;
}
static void reap_child(pid_t child) {
    if (child <= 0) return;
    kill(child, SIGTERM);
    for (unsigned i = 0; i < 10; ++i) {
        if (waitpid(child, NULL, WNOHANG) == child) return;
        struct timespec delay = { .tv_nsec = 100000000 };
        nanosleep(&delay, NULL);
    }
    kill(child, SIGKILL);
    while (waitpid(child, NULL, 0) < 0 && errno == EINTR) {}
}
static int capture(struct sink *s, const char *program, unsigned seconds) {
    int64_t deadline = now_ms() + (int64_t)seconds * 1000;
    for (unsigned attempt = 0; attempt < 3 && !stopped && now_ms() < deadline; ++attempt) {
        int fds[2];
        if (pipe2(fds, O_CLOEXEC)) return -1;
        pid_t child = fork();
        if (!child) {
            close(fds[0]);
            if (dup2(fds[1], STDOUT_FILENO) < 0) _exit(126);
            close(fds[1]);
            execl(program, program, "-b", "all", "-v", "threadtime", "-v", "usec", "-v", "uid", NULL);
            _exit(127);
        }
        close(fds[1]);
        if (child < 0) { close(fds[0]); return -1; }
        int result = 0;
        bool ended = false;
        char data[16384];
        while (!stopped && now_ms() < deadline) {
            struct pollfd p = { .fd = fds[0], .events = POLLIN };
            int ready = poll(&p, 1, 500);
            if (ready < 0 && errno == EINTR) continue;
            if (ready < 0) { result = -1; break; }
            if (ready) {
                ssize_t n = read(fds[0], data, sizeof(data));
                if (n < 0 && errno == EINTR) continue;
                if (n < 0) { result = -1; break; }
                if (!n) { ended = true; break; }
                if (sink_write(s, data, (size_t)n)) { result = -1; break; }
            }
            if (now_ms() - s->last_sync >= 1000 && sync_sink(s)) { result = -1; break; }
        }
        close(fds[0]);
        reap_child(child);
        if (result) return -1;
        if (!ended) return s->first_bytes ? 0 : -1;
        if (note(s, "logcat_exit_retry\n") || sync_sink(s)) return -1;
        struct timespec delay = { .tv_sec = 1 };
        nanosleep(&delay, NULL);
    }
    return (stopped || now_ms() >= deadline) && s->first_bytes ? 0 : -1;
}
#ifdef __ANDROID__
static void read_property(void *buffer, const char *name, const char *value, uint32_t serial) {
    (void)name; (void)serial;
    snprintf(buffer, 256, "%s", value);
}
#endif
#ifndef LDY_CAPTURE_TEST
int main(void) {
    int64_t monotonic_start = now_ms();
    umask(077);
    if (geteuid() != 0) return 1;
    signal(SIGTERM, stop_signal);
    signal(SIGINT, stop_signal);
    signal(SIGPIPE, SIG_IGN);
    int cache = -1;
    for (unsigned i = 0; i < 20 && !stopped; ++i) {
        cache = verified_cache();
        if (cache >= 0) break;
        struct timespec delay = { .tv_nsec = 500000000 };
        nanosleep(&delay, NULL);
    }
    if (cache < 0) { fprintf(stderr, "ldy-capture: cache identity unavailable\n"); return 1; }
    char boot[64] = {0};
    int bootfd = open("/proc/sys/kernel/random/boot_id", O_RDONLY | O_CLOEXEC);
    ssize_t n = bootfd < 0 ? -1 : read(bootfd, boot, sizeof(boot) - 1);
    if (bootfd >= 0) close(bootfd);
    if (n == 37 && boot[36] == '\n') boot[36] = 0;
    else { close(cache); return 1; }
    for (unsigned i = 0; i < 36; ++i)
        if (!(boot[i] >= '0' && boot[i] <= '9') && !(boot[i] >= 'a' && boot[i] <= 'f') &&
            !(boot[i] == '-' && (i == 8 || i == 13 || i == 18 || i == 23))) return 1;
    if (!same_mount(cache, cache) ||
        require_space(cache, FREE_RESERVE + METADATA_BUDGET + CAPTURE_BUDGET)) {
        close(cache); return 1;
    }
    int root = checked_directory(cache, CAPTURE_ROOT, false);
    if (root < 0) { close(cache); return 1; }
    if (!session_capacity(root)) { close(root); close(cache); return 1; }
    struct stat mount_stat;
    if (fstat(cache, &mount_stat)) { close(root); close(cache); return 1; }
    struct sink s = { .directory = checked_directory(root, boot, true), .tail = -1 };
    close(root); close(cache);
    if (s.directory < 0) return 1;
    s.first = fresh_file(s.directory, "first.log");
    s.status = fresh_file(s.directory, "status.txt");
    if (s.first < 0 || s.status < 0) return 1;
    char fingerprint[256] = "unavailable", build_date[256] = "unavailable";
#ifdef __ANDROID__
    const prop_info *property = __system_property_find("ro.build.fingerprint");
    if (property) __system_property_read_callback(property, read_property, fingerprint);
    property = __system_property_find("ro.build.date.utc");
    if (property) __system_property_read_callback(property, read_property, build_date);
#endif
    time_t utc_seconds = time(NULL);
    struct tm utc;
    char timestamp[64] = "unavailable";
    if (gmtime_r(&utc_seconds, &utc))
        strftime(timestamp, sizeof(timestamp), "%Y-%m-%dT%H:%M:%SZ", &utc);
    char message[1024];
    snprintf(message, sizeof(message), "capture_version=1\nboot_id=%s\nlimit_seconds=%d\nmount_dev=%u:%u\nmonotonic_start_ms=%lld\nobserved_utc=%s\nbuild_date_utc=%s\nbuild_fingerprint=%s\nfingerprint_bound=255_bytes\n",
             boot, LIFETIME, major(mount_stat.st_dev), minor(mount_stat.st_dev),
             (long long)monotonic_start, timestamp, build_date, fingerprint);
    int result = note(&s, message) || sync_sink(&s);
#ifdef __ANDROID__
    for (unsigned i = 0; !result && i < 20 && !stopped; ++i) {
        char ready[PROP_VALUE_MAX];
        if (__system_property_get("logd.ready", ready) > 0 && !strcmp(ready, "true")) break;
        struct timespec delay = { .tv_nsec = 500000000 };
        nanosleep(&delay, NULL);
    }
#endif
    if (!result) result = capture(&s, "/system/bin/logcat", LIFETIME);
    snprintf(message, sizeof(message), "end=%s\nfirst_bytes=%zu\ntail_bytes=%zu\n",
             result ? "io_or_child_error" : stopped ? "signal" : "lifetime",
             s.first_bytes, s.tail_bytes);
    if (note(&s, message)) result = -1;
    if (sync_sink(&s)) { fprintf(stderr, "ldy-capture: final sync failed\n"); result = -1; }
    if (s.tail >= 0) close(s.tail);
    close(s.first); close(s.status); close(s.directory);
    return result ? 1 : 0;
}
#endif
