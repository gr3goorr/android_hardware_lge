#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>
#include "Fingerprint.h"

using aidl::android::hardware::biometrics::fingerprint::Fingerprint;

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    std::shared_ptr<Fingerprint> hal = ndk::SharedRefBase::make<Fingerprint>();

    const std::string instance = std::string() + Fingerprint::descriptor + "/default";
    binder_status_t status = AServiceManager_addService(hal->asBinder().get(), instance.c_str());
    CHECK(status == STATUS_OK) << "Failed to register fingerprint AIDL service";

    LOG(INFO) << "Fingerprint AIDL service (LGE) is running.";
    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;
