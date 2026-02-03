package com.oolshik.notificationworker.model;

import java.util.List;
import java.util.Map;

public class ExpoPushResponse {

    private List<ExpoPushTicket> data;

    public List<ExpoPushTicket> getData() {
        return data;
    }

    public void setData(List<ExpoPushTicket> data) {
        this.data = data;
    }

    public static class ExpoPushTicket {
        private String status;
        private String id;
        private String message;
        private Map<String, Object> details;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
}
