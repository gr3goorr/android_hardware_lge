#include <android-base/logging.h>
#include <hardware/hardware.h>
#include "Fingerprint.h"
#include "Session.h"

namespace aidl::android::hardware::biometrics::fingerprint {


std::weak_ptr<ISessionCallback> Fingerprint::mSessionCallback;

Fingerprint::Fingerprint() : mDevice(nullptr), mModule(nullptr) {
    mDevice = openHal();
    if (!mDevice) {
        LOG(ERROR) << "Failed to open legacy HAL for fingerprint";
    }
}

Fingerprint::~Fingerprint() {
    if (mDevice) {
        mDevice->common.close(reinterpret_cast<hw_device_t*>(mDevice));
    }
}

fingerprint_device_t* Fingerprint::openHal() {

    int err;
    const hw_module_t* hw_mdl = nullptr;
    LOG(INFO) << "Opening legacy fingerprint HAL library...";

    if (0 != (err = hw_get_module(FINGERPRINT_HARDWARE_MODULE_ID, &hw_mdl))) {
        LOG(ERROR) << "Can't open fingerprint HW Module, error: " << err;
        return nullptr;
    }

    if (hw_mdl == nullptr) {
        LOG(ERROR) << "No valid fingerprint module";
        return nullptr;
    }

    fingerprint_module_t const* module =
        reinterpret_cast<const fingerprint_module_t*>(hw_mdl);
    if (module->common.methods->open == nullptr) {
        LOG(ERROR) << "No valid open method";
        return nullptr;
    }

    hw_device_t* device = nullptr;

    if (0 != (err = module->common.methods->open(hw_mdl, nullptr, &device))) {
        LOG(ERROR) << "Can't open fingerprint methods, error: " << err;
        return nullptr;
    }

    static const uint16_t kVersion = HARDWARE_MODULE_API_VERSION(2, 1);
    if (kVersion != device->version) {
        LOG(WARNING) << "Wrong fp version. Expected " << kVersion << ", got " << device->version;
    }

    fingerprint_device_t* fp_device =
        reinterpret_cast<fingerprint_device_t*>(device);

    if (0 != (err =
            fp_device->set_notify(fp_device, Fingerprint::notify))) {
        LOG(ERROR) << "Can't register fingerprint module callback, error: " << err;
        return nullptr;
    }

    mModule = module;
    return fp_device;
}

void Fingerprint::notify(const fingerprint_msg_t* msg) {
    if (auto callback = mSessionCallback.lock()) {

        Session* session = reinterpret_cast<Session*>(callback->asBinder().get());
        session->handleCallback(msg);
    } else {
        LOG(WARNING) << "Received 'notify' callback with no active session.";
    }
}

ndk::ScopedAStatus Fingerprint::createSession(int32_t /*sensorId*/, int32_t userId,
                                             const std::shared_ptr<ISessionCallback>& callback,
                                             std::shared_ptr<ISession>* out) {
    if (mDevice == nullptr) {
        LOG(ERROR) << "createSession called on uninitialized HAL";
        return ndk::ScopedAStatus::fromExceptionCode(EX_SERVICE_SPECIFIC);
    }


    if (mSession) {
        mSession->close();
    }

    mSession = ndk::SharedRefBase::make<Session>(mDevice, userId, callback);
    mSessionCallback = callback;


    std::string mutableStorePath = "/data/vendor_de/";
    mutableStorePath += std::to_string(userId) + "/fpdata";
    


    if (mDevice->set_active_group(mDevice, userId, mutableStorePath.c_str()) != 0) {
        LOG(ERROR) << "Failed to set active group (GID) for legacy HAL";
        return ndk::ScopedAStatus::fromExceptionCode(EX_SERVICE_SPECIFIC);
    }

    *out = mSession;
    LOG(INFO) << "Created new Fingerprint AIDL session for userId: " << userId;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Fingerprint::getSensorProps(int32_t /*sensorId*/, std::vector<SensorProps>* out) {
    SensorProps props = {
        .commonProps = {
            .sensorId = 0,
            .sensorStrength = SensorStrength::STRONG,
            .maxEnrollmentsPerUser = 5,
            .halControlsIllumination = false,
        },
        .sensorType = SensorType::REAR, 
        .sensorLocations = {{
            .display = "default",
            .sensorLocationX = 0,
            .sensorLocationY = 0,
            .sensorRadius = 0,
        }},
        .supportsNavigation = true,
        .supportsDetectInteraction = false,
        .halConfig = {},
    };


#ifdef LGE_EGISTEC_UDFPS
    props.sensorType = SensorType::UDFPS_OPTICAL;
    props.commonProps.halControlsIllumination = true; 
#endif

    out->push_back(props);
    return ndk::ScopedAStatus::ok();
}

}  // namespace aidl::android::hardware::biometrics::fingerprint
