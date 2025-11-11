#pragma once

#include <aidl/android/hardware/biometrics/fingerprint/BnFingerprint.h>
#include <hardware/fingerprint.h>
#include "Session.h"

namespace aidl::android::hardware::biometrics::fingerprint {

class Fingerprint : public BnFingerprint {
  public:
    Fingerprint();
    ~Fingerprint();


    ndk::ScopedAStatus createSession(int32_t sensorId, int32_t userId,
                                     const std::shared_ptr<ISessionCallback>& callback,
                                     std::shared_ptr<ISession>* out) override;
    ndk::ScopedAStatus getSensorProps(int32_t sensorId, std::vector<SensorProps>* out) override;

    static void notify(const fingerprint_msg_t* msg);

  private:
    fingerprint_device_t* openHal();
    fingerprint_device_t* mDevice;
    fingerprint_module_t const* mModule;
    std::shared_ptr<ISession> mSession;

    static std::weak_ptr<ISessionCallback> mSessionCallback;
};

}  // namespace aidl::android::hardware::biometrics::fingerprint
