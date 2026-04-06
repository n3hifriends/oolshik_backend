package com.oolshik.backend.service;

import com.oolshik.backend.aop.LoggingAspect;
import com.oolshik.backend.web.dto.AuthDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LoggingAspectSanitizationTest {

    @Test
    void redactsOtpAndPhoneValues(CapturedOutput output) {
        LoggingAspect aspect = new LoggingAspect();
        TestTarget target = new TestTarget();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        TestTarget proxy = factory.getProxy();

        proxy.submit(new AuthDtos.OtpVerify("+919876543210", "123456", "Nitin", "n@example.com"));

        assertThat(output.getOut()).doesNotContain("+919876543210");
        assertThat(output.getOut()).doesNotContain("123456");
        assertThat(output.getOut()).contains("[redacted]");
    }

    static class TestTarget {
        public void submit(AuthDtos.OtpVerify request) {
        }
    }
}
