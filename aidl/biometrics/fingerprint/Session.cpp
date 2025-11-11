#include <android-base/logging.h>
#include <android-base/file.h>
#include <fstream>
#include "Session.h"

namespace aidl::android::hardware::biometrics::fingerprint {


static Error vendorErrorToAidlError(int32_t error, int32_t* vendorCode) {
    *vendorCode = 0;
    switch (error) {
        case FINGERPRINT_ERROR_HW_UNAVAILABLE: return Error::HW_UNAVAILABLE;
        case FINGERPRINT_ERROR_UNABLE_TO_PROCESS: return Error::UNABLE_TO_PROCESS;
        case FINGERPRINT_ERROR_TIMEOUT: return Error::TIMEOUT;
        case FINGERPRINT_ERROR_NO_SPACE: return Error::NO_SPACE;
        case FINGERPRINT_ERROR_CANCELED: return Error::CANCELED;
        case FINGERPRINT_ERROR_UNABLE_TO_REMOVE: return Error::UNABLE_TO_REMOVE;
        case FINGERPRINT_ERROR_LOCKOUT: return Error::LOCKOUT;
        default:
            if (error >= FINGERPRINT_ERROR_VENDOR_BASE) {
                *vendorCode = error - FINGERPRINT_ERROR_VENDOR_BASE;
                return Error::VENDOR;
            }
            return Error::UNKNOWN;
    }
}


static AcquiredInfo vendorAcquiredToAidlAcquired(int32_t info, int32_t* vendorCode) {
    *vendorCode = 0;
    switch (info) {
        case FINGERPRINT_ACQUIRED_GOOD: return AcquiredInfo::GOOD;
        case FINGERPRINT_ACQUIRED_PARTIAL: return AcquiredInfo::PARTIAL;
        case FINGERPRINT_ACQUIRED_INSUFFICIENT: return AcquiredInfo::INSUFFICIENT;
        case FINGERPRINT_ACQUIRED_IMAGER_DIRTY: return AcquiredInfo::SENSOR_DIRTY;
        case FINGERPRINT_ACQUIRED_TOO_SLOW: return AcquiredInfo::TOO_SLOW;
        case FINGERPRINT_ACQUIRED_TOO_FAST: return AcquiredInfo::TOO_FAST;
        case FINGERPRINT_ACQUIRED_DETECTED: return AcquiredInfo::START;
        default:
            if (info >= FINGERPRINT_ACQUIRED_VENDOR_BASE) {
                *vendorCode = info - FINGERPRINT_ACQUIRED_VENDOR_BASE;
                return AcquiredInfo::VENDOR;
            }
            return AcquiredInfo::UNKNOWN;
    }
}



Session::Session(fingerprint_device_t* device, int32_t userId,
                 const std::shared_ptr<ISessionCallback>& callback)
    : mDevice(device), mUserId(userId), mCallback(callback) {


#ifdef LGE_EGISTEC_UDFPS
    hbmFodEnabled = false;
    if (std::ifstream(FOD_HBM_LEGACY_PATH).good()) {
        mHbmPath = FOD_HBM_LEGACY_PATH;
    } else if (std::ifstream(FOD_HBM_PATH).good()) {
        mHbmPath = FOD_HBM_PATH;
    } else {
        mHbmPath = "/dev/null";
        LOG(ERROR) << "HBM FOD sysfs path not found!";
    }
#endif
    LOG(INFO) << "AIDL Session created.";
}

Session::~Session() {
    if (mDevice) {
        close();
    }
}



ndk::ScopedAStatus Session::generateChallenge() {
    std::lock_guard<std::mutex> lock(mLock);
    uint64_t challenge = mDevice->pre_enroll(mDevice);
    LOG(INFO) << "generateChallenge: " << challenge;
    mCallback->onChallengeGenerated(challenge);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::revokeChallenge(int64_t challenge) {
    std::lock_guard<std::mutex> lock(mLock);
    mDevice->post_enroll(mDevice);
    LOG(INFO) << "revokeChallenge: " << challenge;
    mCallback->onChallengeRevoked(challenge);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::enroll(const HwEnrollmentStageInfo& /*stageInfo*/,
                                     const std::vector<uint8_t>& hat) {
    std::lock_guard<std::mutex> lock(mLock);
    const hw_auth_token_t* authToken =
        reinterpret_cast<const hw_auth_token_t*>(hat.data());

    LOG(INFO) << "enroll: GID=" << mUserId;
    int ret = mDevice->enroll(mDevice, authToken, mUserId, 30 /* timeout */);
    if (ret != 0) {
        LOG(ERROR) << "enroll failed, error: " << ret;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::authenticate(int64_t operationId) {
    std::lock_guard<std::mutex> lock(mLock);
    LOG(INFO) << "authenticate: opId=" << operationId << ", GID=" << mUserId;
    int ret = mDevice->authenticate(mDevice, operationId, mUserId);
    if (ret != 0) {
        LOG(ERROR) << "authenticate failed, error: " << ret;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::detectInteraction() {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Session::enumerateEnrollments() {
    std::lock_guard<std::mutex> lock(mLock);
    LOG(INFO) << "enumerateEnrollments";
    mDevice->enumerate(mDevice);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::removeEnrollments(const std::vector<int32_t>& enrollmentIds) {
    std::lock_guard<std::mutex> lock(mLock);
    for (int32_t fid : enrollmentIds) {
        LOG(INFO) << "removeEnrollments: GID=" << mUserId << ", FID=" << fid;
        mDevice->remove(mDevice, mUserId, fid);
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::getAuthenticatorId() {
    std::lock_guard<std::mutex> lock(mLock);
    uint64_t id = mDevice->get_authenticator_id(mDevice);
    LOG(INFO) << "getAuthenticatorId: " << id;
    mCallback->onAuthenticatorIdRetrieved(id);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::invalidateAuthenticatorId() {
    LOG(WARNING) << "invalidateAuthenticatorId is not supported by legacy HAL";
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Session::resetLockout(const std::vector<uint8_t>& /*hat*/) {
    LOG(WARNING) << "resetLockout is not supported by legacy HAL";
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Session::close() {
    std::lock_guard<std::mutex> lock(mLock);
    LOG(INFO) << "close (canceling operations)";
    mDevice->cancel(mDevice);
#ifdef LGE_EGISTEC_UDFPS

    if (hbmFodEnabled) {
        std::lock_guard<std::mutex> hbmLock(mSetHbmFodMutex);
        uint32_t param = 0;
        mDevice->do_extra_api_in(FINGERPRINT_LGE_SCAN_STOP, &param);
        setFodHbm(false);
        resetLgeTouchPanel();
        hbmFodEnabled = false;
    }
#endif
    mCallback->onSessionClosed();
    return ndk::ScopedAStatus::ok();
}



ndk::ScopedAStatus Session::onPointerDown(const PointerContext& /*context*/) {
#ifdef LGE_EGISTEC_UDFPS

    std::lock_guard<std::mutex> lock(mSetHbmFodMutex);
    if (!hbmFodEnabled) {
        LOG(INFO) << "onPointerDown: Enabling HBM";
        uint32_t param = 0;
        mDevice->do_extra_api_in(FINGERPRINT_LGE_SCAN_START, &param);
        setFodHbm(true);
        hbmFodEnabled = true;
    }
    return ndk::ScopedAStatus::ok();
#else
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
#endif
}

ndk::ScopedAStatus Session::onPointerUp(const PointerContext& /*context*/) {
#ifdef LGE_EGISTEC_UDFPS

    std::lock_guard<std::mutex> lock(mSetHbmFodMutex);
    if (hbmFodEnabled) {
        LOG(INFO) << "onPointerUp: Disabling HBM";
        uint32_t param = 0;
        mDevice->do_extra_api_in(FINGERPRINT_LGE_SCAN_STOP, &param);
        setFodHbm(false);
        resetLgeTouchPanel();
        hbmFodEnabled = false;
    }
    return ndk::ScopedAStatus::ok();
#else
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
#endif
}

ndk::ScopedAStatus Session::onUiReady() {

    LOG(INFO) << "onUiReady";
    return ndk::ScopedAStatus::ok();
}



ndk::ScopedAStatus Session::onContextChanged(const Context& /*context*/) {
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::getExtension(const std::string& name, std::shared_ptr<IBinder>* out) {

    if (name == ILgeFingerprintExtension::descriptor) {
        *out = this->asBinder();
        LOG(INFO) << "Returning LGE extension implementation";
        return ndk::ScopedAStatus::ok();
    }
    LOG(WARNING) << "Extension not found: " << name;
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}



ndk::ScopedAStatus Session::extraCmd(FingerCmdLge cmd, const std::vector<uint8_t>& /*cmdParam*/) {
    std::lock_guard<std::mutex> lock(mLock);
    uint32_t param = 0;
    fingerprint_lge_extra_cmd_t legacyCmd;

    switch(cmd) {
        case FingerCmdLge::SCAN_START:
            legacyCmd = FINGERPRINT_LGE_SCAN_START;
            LOG(INFO) << "extraCmd: SCAN_START";
            break;
        case FingerCmdLge::SCAN_STOP:
            legacyCmd = FINGERPRINT_LGE_SCAN_STOP;
            LOG(INFO) << "extraCmd: SCAN_STOP";
            break;
        case FingerCmdLge::NAVIGATION_START:
            legacyCmd = FINGERPRINT_LGE_NAVIGATION_START;
            LOG(INFO) << "extraCmd: NAVIGATION_START";
            break;
        case FingerCmdLge::NAVIGATION_STOP:
            legacyCmd = FINGERPRINT_LGE_NAVIGATION_STOP;
            LOG(INFO) << "extraCmd: NAVIGATION_STOP";
            break;
        default:
            LOG(ERROR) << "extraCmd: Unknown command";
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }


    mDevice->do_extra_api_in(legacyCmd, &param);
    return ndk::ScopedAStatus::ok();
}


#ifdef LGE_EGISTEC_UDFPS
void Session::setFodHbm(bool status) {
    //
    LOG(INFO) << "setFodHbm: " << (status ? "1" : "0") << " to " << mHbmPath;
    ::android::base::WriteStringToFile(status ? "1" : "0", mHbmPath);
}

void Session::resetLgeTouchPanel(void) {
    //
    LOG(INFO) << "resetLgeTouchPanel";
    ::android::base::WriteStringToFile("4", LGE_TOUCH_RESET_PATH);
}
#endif


void Session::handleCallback(const fingerprint_msg_t* msg) {

    if (!mCallback) {
        LOG(ERROR) << "handleCallback called, but mCallback is null!";
        return;
    }

    switch (msg->type) {
        case FINGERPRINT_ERROR: {
            int32_t vendorCode = 0;
            Error aidlError = vendorErrorToAidlError(msg->data.error, &vendorCode);
            LOG(INFO) << "handleCallback: onError: " << static_cast<int32_t>(aidlError)
                      << ", vendorCode: " << vendorCode;
            mCallback->onError(aidlError, vendorCode);
            break;
        }
        case FINGERPRINT_ACQUIRED: {
            int32_t vendorCode = 0;
            AcquiredInfo aidlAcquired = vendorAcquiredToAidlAcquired(msg->data.acquired.acquired_info, &vendorCode);
            LOG(INFO) << "handleCallback: onAcquired: " << static_cast<int32_t>(aidlAcquired)
                      << ", vendorCode: " << vendorCode;
            mCallback->onAcquired(aidlAcquired, vendorCode);
            break;
        }
        case FINGERPRINT_TEMPLATE_ENROLLING: {
            EnrollmentStageInfo info = {
                .enrollmentId = msg->data.enroll.finger.fid,
                .userId = msg->data.enroll.finger.gid,
                .remaining = msg->data.enroll.samples_remaining
            };
            LOG(INFO) << "handleCallback: onEnrollmentProgress: fid=" << info.enrollmentId
                      << ", rem=" << info.remaining;
            mCallback->onEnrollmentProgress(info);
            break;
        }
        case FINGERPRINT_TEMPLATE_REMOVED: {
            LOG(INFO) << "handleCallback: onEnrollmentsRemoved: fid=" << msg->data.removed.finger.fid;
            mCallback->onEnrollmentsRemoved({msg->data.removed.finger.fid});
            break;
        }
        case FINGERPRINT_AUTHENTICATED: {
            if (msg->data.authenticated.finger.fid != 0) {
                AuthenticationResult res;
                res.fingerprintId = msg->data.authenticated.finger.fid;
                res.userId = msg->data.authenticated.finger.gid;
                const uint8_t* hat = reinterpret_cast<const uint8_t*>(&msg->data.authenticated.hat);
                res.hat.assign(hat, hat + sizeof(hw_auth_token_t));
                
                LOG(INFO) << "handleCallback: onAuthenticationSucceeded: fid=" << res.fingerprintId;
                mCallback->onAuthenticationSucceeded(res);
            } else {
                LOG(INFO) << "handleCallback: onAuthenticationFailed";
                mCallback->onAuthenticationFailed();
            }
            break;
        }
        case FINGERPRINT_TEMPLATE_ENUMERATING: {
            EnrollmentInfo info = {
                .enrollmentId = msg->data.enumerated.finger.fid,
                .userId = msg->data.enumerated.finger.gid
            };
            LOG(INFO) << "handleCallback: onEnrollmentsEnumerated: fid=" << info.enrollmentId;
            mCallback->onEnrollmentsEnumerated({info});
            break;
        }
    }
}

}  // namespace aidl::android::hardware::biometrics::fingerprint
