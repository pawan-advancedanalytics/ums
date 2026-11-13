package com.ums.messaging;

import com.ums.messaging.application.PublisherService;
import com.ums.messaging.api.MessageController;
import com.ums.messaging.error.PublishException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
class MessageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PublisherService publisher;
    static final String EVENT = """
            {"eventId":"d163efae-625d-44dd-b944-7243153196ca","eventType":"user.created",
             "version":"1.0","source":"test","timestamp":"2026-11-13T08:00:00Z",
             "correlationId":"request-1","payload":{"userId":"101"}}
            """;

    @Test void acceptsValidEnvelope() throws Exception {
        mvc.perform(post("/api/v1/messages/publish").contentType("application/json").content(EVENT))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("BROKER_CONFIRMED"));
        verify(publisher).publish(any());
    }
    @Test void rejectsMissingFields() throws Exception {
        mvc.perform(post("/api/v1/messages/publish").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(publisher);
    }
    @Test void rejectsWildcardRouting() throws Exception {
        mvc.perform(post("/api/v1/messages/publish").contentType("application/json").content(EVENT.replace("user.created", "user.*")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(publisher);
    }
    @Test void reportsBrokerFailure() throws Exception {
        doThrow(new PublishException("Broker unavailable")).when(publisher).publish(any());
        mvc.perform(post("/api/v1/messages/publish").contentType("application/json").content(EVENT))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("Broker unavailable"));
    }
}
