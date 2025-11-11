#pragma once

#include <aidl/android/hardware/biometrics/fingerprint/BnSession.h>
#include <aidl/vendor/lge/hardware/biometrics/fingerprint/BnLgeFingerprintExtension.h>
#include <hardware/fingerprint.h>
#include <mutex>

#ifdef LGE_EGISTEC_UDFPS
#define FOD_HBM_LEGACY_PATH "/sys/devices/virtual/panel/brightness/fp_lhbm"
#define FOD_HBM_PATH "/sys/devices/virtual/panel/panel-0/brightness/fp_lhbm"
#define LGE_TOUCH_RESET_PATH "/sys/devices/virtual/input/lge_touch/reset_ctrl"
#endif

namespace aidl::android::hardware::biometrics::fingerprint {

using ::aidl::vendor::lge::hardware::biometrics::fingerprint::BnLgeFingerprintExtension;
using ::aidl::vendor::lge::hardware::biometrics::fingerprint::FingerCmdLge;

class Session : public BnSession, public BnLgeFingerprintExtension, public std::enable_shared_from_this<Session> {
  public:
    Session(fingerprint_device_t* device, int32_t userId,
            const std::shared_ptr<ISessionCallback>& callback);
    ~Session();


    ndk::ScopedAStatus generateChallenge() override;
    ndk::ScopedAStatus revokeChallenge(int64_t challenge) override;
    ndk::ScopedAStatus enroll(const HwEnrollmentStageInfo& stageInfo,
                              const std::vector<uint8_t>& hat) override;
    ndk::ScopedAStatus authenticate(int64_t operationId) override;
    ndk::ScopedAStatus detectInteraction() override;
    ndk::ScopedAStatus enumerateEnrollments() override;
    ndk::ScopedAStatus removeEnrollments(const std::vector<int32_t>& enrollmentIds) override;
    ndk::ScopedAStatus getAuthenticatorId() override;
    ndk::ScopedAStatus invalidateAuthenticatorId() override;
    ndk::ScopedAStatus resetLockout(const std::vector<uint8_t>& hat) override;
    ndk::ScopedAStatus close() override;


    ndk::ScopedAStatus onPointerDown(const PointerContext& context) override;
    ndk::ScopedAStatus onPointerUp(const PointerContext& context) override;
    ndk::ScopedAStatus onUiReady() override;


    ndk::ScopedAStatus onContextChanged(const Context& context) override;
    ndk::ScopedAStatus getExtension(const std::string& name, std::shared_ptr<IBinder>* out) override;


    ndk::ScopedAStatus extraCmd(FingerCmdLge cmd, const std::vector<uint8_t>& cmdParam) override;


    void handleCallback(const fingerprint_msg_t* msg);
    std::shared_ptr<ISessionCallback> getCallback() { return mCallback; }

  private:
    fingerprint_device_t* mDevice;
    int32_t mUserId;
    std::shared_ptr<ISessionCallback> mCallback;
    std::mutex mLock;


#ifdef LGE_EGISTEC_UDFPS
    void setFodHbm(bool status);
    void resetLgeTouchPanel(void);
    bool hbmFodEnabled;
    std::string mHbmPath;
    std::mutex mSetHbmFodMutex;
#endif
};

}  // namespace aidl::android::hardware::biometrics::fingerprint
