package com.sixeyes;

import com.sixeyes.service.PythonClientService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("dev")
class SixEyesApplicationTests {

    @MockitoBean
    PythonClientService pythonClient;

    @Test
    void contextLoads() {
    }
}
