package com.oolshik.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private final Phone phone = new Phone();
    private final Google google = new Google();

    public Phone getPhone() {
        return phone;
    }

    public Google getGoogle() {
        return google;
    }

    public static class Phone {
        private boolean otpEnabled = true;

        public boolean isOtpEnabled() {
            return otpEnabled;
        }

        public void setOtpEnabled(boolean otpEnabled) {
            this.otpEnabled = otpEnabled;
        }
    }

    public static class Google {
        private boolean enabled = true;
        private boolean requirePhone = true;
        private boolean autoLinkByEmail = false;
        private List<String> allowedClientIds = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequirePhone() {
            return requirePhone;
        }

        public void setRequirePhone(boolean requirePhone) {
            this.requirePhone = requirePhone;
        }

        public boolean isAutoLinkByEmail() {
            return autoLinkByEmail;
        }

        public void setAutoLinkByEmail(boolean autoLinkByEmail) {
            this.autoLinkByEmail = autoLinkByEmail;
        }

        public List<String> getAllowedClientIds() {
            return allowedClientIds;
        }

        public void setAllowedClientIds(List<String> allowedClientIds) {
            this.allowedClientIds = allowedClientIds == null ? new ArrayList<>() : allowedClientIds;
        }
    }
}
