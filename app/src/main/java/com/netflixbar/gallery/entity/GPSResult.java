package com.netflixbar.gallery.entity;

/** 腾讯位置服务逆地址解析响应。 */
public class GPSResult {
    private Integer status;
    private ResultDTO result;

    public boolean isSuccess() {
        return status != null && status == 0;
    }

    public ResultDTO getResult() {
        return result;
    }

    public static class ResultDTO {
        private String address;
        private FormattedAddresses formatted_addresses;

        public String getAddress() {
            if (address != null && !address.trim().isEmpty()) {
                return address;
            }
            return formatted_addresses == null ? null : formatted_addresses.recommend;
        }
    }

    private static class FormattedAddresses {
        private String recommend;
    }
}
