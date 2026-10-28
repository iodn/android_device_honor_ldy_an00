// SPDX-License-Identifier: Apache-2.0
#include <CL/cl.h>
#include <jni.h>
#include <dlfcn.h>

#include <array>
#include <cmath>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
void check(cl_int status, const char* operation) {
    if (status != CL_SUCCESS) {
        throw std::runtime_error(std::string(operation) + ": " + std::to_string(status));
    }
}

template <typename T>
struct ClHandle {
    T value;
    cl_int (*release)(T);
    ~ClHandle() {
        if (value) release(value);
    }
};

std::string probe() {
    std::ifstream attr("/proc/self/attr/current");
    std::string domain;
    std::getline(attr, domain);
    if (domain.find(":untrusted_app") == std::string::npos) {
        throw std::runtime_error("Expected an untrusted application SELinux domain");
    }

    std::unique_ptr<void, decltype(&dlclose)> library(dlopen("libOpenCL.so", RTLD_NOW), dlclose);
    if (!library) {
        const char* error = dlerror();
        throw std::runtime_error(error ? error : "Cannot load public libOpenCL.so");
    }
#define LOAD(name) \
    auto name = reinterpret_cast<decltype(&::name)>(dlsym(library.get(), #name)); \
    if (!name) throw std::runtime_error("Missing OpenCL entry point: " #name)
    LOAD(clGetPlatformIDs);
    LOAD(clGetDeviceIDs);
    LOAD(clGetDeviceInfo);
    LOAD(clCreateContext);
    LOAD(clReleaseContext);
    LOAD(clCreateCommandQueue);
    LOAD(clReleaseCommandQueue);
    LOAD(clCreateProgramWithSource);
    LOAD(clBuildProgram);
    LOAD(clGetProgramBuildInfo);
    LOAD(clReleaseProgram);
    LOAD(clCreateKernel);
    LOAD(clReleaseKernel);
    LOAD(clCreateBuffer);
    LOAD(clReleaseMemObject);
    LOAD(clSetKernelArg);
    LOAD(clEnqueueNDRangeKernel);
    LOAD(clEnqueueReadBuffer);
#undef LOAD

    cl_uint count = 0;
    check(clGetPlatformIDs(0, nullptr, &count), "clGetPlatformIDs(count)");
    if (count == 0 || count > 64) throw std::runtime_error("Invalid OpenCL platform count");
    std::vector<cl_platform_id> platforms(count);
    check(clGetPlatformIDs(count, platforms.data(), nullptr), "clGetPlatformIDs");
    cl_device_id device = nullptr;
    for (auto platform : platforms) {
        cl_int status = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, nullptr);
        if (status == CL_DEVICE_NOT_FOUND) continue;
        check(status, "clGetDeviceIDs(GPU)");
        if (device) break;
    }
    if (!device) throw std::runtime_error("No GPU device; CPU fallback is not accepted");

    cl_device_type type = 0;
    check(clGetDeviceInfo(device, CL_DEVICE_TYPE, sizeof(type), &type, nullptr),
          "clGetDeviceInfo(type)");
    if (!(type & CL_DEVICE_TYPE_GPU)) throw std::runtime_error("Selected device is not a GPU");
    std::array<char, 256> name{};
    check(clGetDeviceInfo(device, CL_DEVICE_NAME, name.size(), name.data(), nullptr),
          "clGetDeviceInfo(name)");
    name.back() = 0;

    cl_int status = CL_SUCCESS;
    ClHandle<cl_context> context{
            clCreateContext(nullptr, 1, &device, nullptr, nullptr, &status), clReleaseContext};
    check(status, "clCreateContext");
    if (!context.value) throw std::runtime_error("Null OpenCL context");
    ClHandle<cl_command_queue> queue{
            clCreateCommandQueue(context.value, device, 0, &status), clReleaseCommandQueue};
    check(status, "clCreateCommandQueue");
    if (!queue.value) throw std::runtime_error("Null OpenCL queue");

    const char* source =
            "__kernel void add(__global const float* a, __global const float* b,"
            "                  __global float* c) {"
            "  size_t i = get_global_id(0); c[i] = a[i] + b[i]; }";
    ClHandle<cl_program> program{
            clCreateProgramWithSource(context.value, 1, &source, nullptr, &status),
            clReleaseProgram};
    check(status, "clCreateProgramWithSource");
    if (!program.value) throw std::runtime_error("Null OpenCL program");
    status = clBuildProgram(program.value, 1, &device, nullptr, nullptr, nullptr);
    if (status != CL_SUCCESS) {
        std::array<char, 4096> log{};
        size_t length = 0;
        clGetProgramBuildInfo(program.value, device, CL_PROGRAM_BUILD_LOG, 0, nullptr, &length);
        if (length > 0 && length <= log.size()) {
            clGetProgramBuildInfo(program.value, device, CL_PROGRAM_BUILD_LOG,
                                  log.size(), log.data(), nullptr);
        }
        log.back() = 0;
        throw std::runtime_error("clBuildProgram: " + std::to_string(status) + " " + log.data());
    }
    ClHandle<cl_kernel> kernel{clCreateKernel(program.value, "add", &status), clReleaseKernel};
    check(status, "clCreateKernel");
    if (!kernel.value) throw std::runtime_error("Null OpenCL kernel");

    constexpr size_t kCount = 256;
    std::array<float, kCount> a{}, b{}, output{};
    for (size_t i = 0; i < kCount; ++i) {
        a[i] = static_cast<float>(i) * 0.25f;
        b[i] = 1.0f - static_cast<float>(i) * 0.125f;
    }
    ClHandle<cl_mem> inputA{
            clCreateBuffer(context.value, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                           sizeof(a), a.data(), &status), clReleaseMemObject};
    check(status, "clCreateBuffer(A)");
    ClHandle<cl_mem> inputB{
            clCreateBuffer(context.value, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                           sizeof(b), b.data(), &status), clReleaseMemObject};
    check(status, "clCreateBuffer(B)");
    ClHandle<cl_mem> result{
            clCreateBuffer(context.value, CL_MEM_WRITE_ONLY, sizeof(output), nullptr, &status),
            clReleaseMemObject};
    check(status, "clCreateBuffer(output)");
    check(clSetKernelArg(kernel.value, 0, sizeof(cl_mem), &inputA.value), "clSetKernelArg(A)");
    check(clSetKernelArg(kernel.value, 1, sizeof(cl_mem), &inputB.value), "clSetKernelArg(B)");
    check(clSetKernelArg(kernel.value, 2, sizeof(cl_mem), &result.value), "clSetKernelArg(output)");
    size_t work = kCount;
    check(clEnqueueNDRangeKernel(queue.value, kernel.value, 1, nullptr, &work, nullptr,
                                 0, nullptr, nullptr), "clEnqueueNDRangeKernel");
    check(clEnqueueReadBuffer(queue.value, result.value, CL_TRUE, 0, sizeof(output), output.data(),
                              0, nullptr, nullptr), "clEnqueueReadBuffer");
    for (size_t i = 0; i < kCount; ++i) {
        if (!std::isfinite(output[i]) || std::fabs(output[i] - (a[i] + b[i])) > 0.000001f) {
            throw std::runtime_error("GPU result mismatch at index " + std::to_string(i));
        }
    }
    return "PASS OpenCL GPU vector addition: 256 verified results; device=" +
            std::string(name.data()) + "; domain=" + domain;
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_org_lineageos_ldy_1an00_gputests_GpuCompatibilityTest_runOpenClProbe(JNIEnv* env, jclass) {
    try {
        std::string result = probe();
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& error) {
        jclass exception = env->FindClass("java/lang/AssertionError");
        if (exception) env->ThrowNew(exception, error.what());
        return nullptr;
    }
}
