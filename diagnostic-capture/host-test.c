#define LDY_CAPTURE_TEST
#include "ldy-capture.c"
#include <assert.h>

static struct sink create_sink(int parent, const char *name) {
    struct sink s = { .directory = checked_directory(parent, name, true), .tail = -1 };
    assert(s.directory >= 0);
    s.first = fresh_file(s.directory, "first.log");
    s.status = fresh_file(s.directory, "status.txt");
    assert(s.first >= 0 && s.status >= 0 && sync_sink(&s) == 0);
    return s;
}
static void close_sink(struct sink *s) {
    if (s->tail >= 0) close(s->tail);
    close(s->status);close(s->first);close(s->directory);
}
int main(int argc, char **argv) {
    assert(argc == 3);
    umask(077);
    signal(SIGPIPE, SIG_IGN);
    assert(mount_matches("1 2 8:3 / /cache rw,nosuid - ext4 /dev/block/cache rw,discard",8,3,1));
    assert(!mount_matches("1 2 8:3 / /cache ro - ext4 /dev/block/cache rw",8,3,1));
    assert(!mount_matches("1 2 8:3 /sub /cache rw - ext4 /dev/block/cache rw",8,3,1));
    assert(!mount_matches("1 2 8:3 / /cache rw - tmpfs none rw",8,3,1));
    assert(!mount_matches("1 2 8:3 / /cache rw - ext4 none ro",8,3,1));
    assert(!mount_matches("1 2 8:4 / /cache rw - ext4 none rw",8,3,1));
    assert(!mount_matches("1 2 8:3 / /cache rw - ext4 none rw",8,3,2));
    struct statx left={.stx_mask=STATX_MNT_ID,.stx_mnt_id=8,.stx_dev_major=8,.stx_dev_minor=3};
    struct statx right=left;
    assert(mount_identity_equal(&left,&right));
    right.stx_mnt_id=9;assert(!mount_identity_equal(&left,&right));
    right=left;right.stx_mask=0;assert(!mount_identity_equal(&left,&right));
    puts("mount_identity_parser_and_same_device_bind_id=PASS");
    struct statvfs budget={.f_frsize=4096,.f_bavail=2048};
    assert(space_sufficient(&budget,FREE_RESERVE));
    assert(!space_sufficient(&budget,FREE_RESERVE+1));
    budget.f_bavail=UINT64_MAX;assert(!space_sufficient(&budget,1));
    budget.f_frsize=0;assert(!space_sufficient(&budget,1));
    puts("available_space_boundary_and_overflow=PASS");
    assert(mkdir(argv[1],0700)==0);
    int parent=open(argv[1],O_RDONLY|O_DIRECTORY);
    assert(parent>=0);
    assert(same_mount(parent,parent));
    int proc=open("/proc",O_RDONLY|O_DIRECTORY);assert(proc>=0);
    assert(!same_mount(parent,proc));close(proc);
    assert(!same_mount(parent,-1));
    puts("actual_statx_mount_id_and_failclosed=PASS");
    assert(symlinkat("missing",parent,"link")==0);
    assert(checked_directory(parent,"link",false)<0);
    int d=checked_directory(parent,"once",true);assert(d>=0);close(d);
    assert(checked_directory(parent,"once",true)<0);
    puts("nofollow_exclusive_directory=PASS");

    int cap=checked_directory(parent,"capacity",true);assert(cap>=0);
    assert(session_capacity(cap));
    for(unsigned i=0;i<4;i++){char n[8];snprintf(n,sizeof(n),"s%u",i);int f=fresh_file(cap,n);assert(f>=0);close(f);}
    assert(!session_capacity(cap));close(cap);puts("four_session_total_bound=PASS");

    struct sink s=create_sink(parent,"flood");
    char buffer[16384];memset(buffer,'A',sizeof(buffer));
    for (unsigned i=0;i<(24U*1024U*1024U)/sizeof(buffer);i++)
        assert(sink_write(&s,buffer,sizeof(buffer))==0);
    assert(s.first_bytes==SEGMENT);
    assert(note(&s,"end=test\n")==0 && sync_sink(&s)==0);
    struct stat st;assert(fstat(s.first,&st)==0 && st.st_size==SEGMENT);
    for(unsigned i=0;i<TAILS;i++){
        char name[32];snprintf(name,sizeof(name),"tail-%u.log",i);
        assert(fstatat(s.directory,name,&st,AT_SYMLINK_NOFOLLOW)==0);
        assert(S_ISREG(st.st_mode) && st.st_size<=SEGMENT);
    }
    assert(fstat(s.status,&st)==0 && st.st_size<=4096);
    close_sink(&s);puts("real_24MiB_flood_first_tail_bounds=PASS");

    s=create_sink(parent,"budget-failure");
    int realdir=s.directory;s.directory=-1;
    assert(sink_write(&s,"x",1)<0 && errno==EBADF);
    assert(fstat(s.first,&st)==0 && st.st_size==0);
    s.directory=realdir;close_sink(&s);
    puts("real_space_query_failure_before_payload=PASS");

    s=create_sink(parent,"full");close(s.first);
    s.first=open("/dev/full",O_WRONLY);assert(s.first>=0);
    assert(sink_write(&s,"x",1)<0 && errno==ENOSPC);
    close_sink(&s);puts("actual_ENOSPC_write=PASS");

    s=create_sink(parent,"sync");
    int actualdir=s.directory;s.directory=open("/dev/null",O_RDONLY);assert(s.directory>=0);
    assert(sync_sink(&s)<0);
    close(s.directory);s.directory=actualdir;
    int status=openat(s.directory,"status.txt",O_RDONLY);assert(status>=0);
    char message[100]={0};assert(read(status,message,sizeof(message)-1)>0);
    assert(strstr(message,"sync_error="));close(status);close_sink(&s);
    puts("actual_fsync_failure_record=PASS");

    s=create_sink(parent,"blocked-tail");
    s.first_bytes=SEGMENT;
    assert(symlinkat("/dev/null",s.directory,"tail-0.log")==0);
    assert(sink_write(&s,"x",1)<0);
    close_sink(&s);puts("tail_symlink_refusal=PASS");

    s=create_sink(parent,"lifetime");
    setenv("LDY_TEST_CHILD","sleep",1);
    int64_t start=now_ms();
    assert(capture(&s,argv[2],1)==0);
    assert(now_ms()-start>=1000 && now_ms()-start<3000);
    assert(waitpid(-1,NULL,WNOHANG)<0 && errno==ECHILD);
    close_sink(&s);puts("owned_child_lifetime_cleanup=PASS");

    s=create_sink(parent,"exits");
    setenv("LDY_TEST_CHILD","exit",1);
    start=now_ms();
    assert(capture(&s,argv[2],5)<0);
    assert(now_ms()-start<4500);
    assert(waitpid(-1,NULL,WNOHANG)<0 && errno==ECHILD);
    close_sink(&s);puts("bounded_no_logd_child_retry=PASS");
    close(parent);
    return 0;
}
