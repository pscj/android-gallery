package com.netflixbar.gallery.entity;

public class GPSResult {
    int status;
    ResultDTO result;

    public ResultDTO getResult() {
        return result;
    }

    public int getStatus() {
        return status;
    }


    public static class ResultDTO{
        public String getFormatted_address() {
            return formatted_address;
        }

        String formatted_address;
    }
}

