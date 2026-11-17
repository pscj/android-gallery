package com.netflixbar.gallery;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class TencentGeocoderTest {
    @Test public void sendsExifCoordinatesAsGpsInLatitudeLongitudeOrder() {
        Map<String, String> parameters = TencentGeocoder.parameters(" test-key ", "39.984154,116.307490");
        assertEquals("test-key", parameters.get("key"));
        assertEquals("39.984154,116.307490", parameters.get("location"));
        assertEquals("1", parameters.get("coord_type"));
        assertEquals("0", parameters.get("get_poi"));
        assertEquals("json", parameters.get("output"));
    }

    @Test public void readsTencentAddressBeforeRecommendedDescription() {
        assertEquals("北京市海淀区海淀大街", TencentGeocoder.parseAddress(
                "{\"status\":0,\"result\":{\"address\":\"北京市海淀区海淀大街\","
                        + "\"formatted_addresses\":{\"recommend\":\"海淀区某地标\"}}}"));
    }

    @Test public void fallsBackToRecommendedDescription() {
        assertEquals("海淀区某地标", TencentGeocoder.parseAddress(
                "{\"status\":0,\"result\":{\"address\":\" \","
                        + "\"formatted_addresses\":{\"recommend\":\"海淀区某地标\"}}}"));
    }

    @Test public void rejectsServiceErrorsAndIncompleteResponses() {
        assertNull(TencentGeocoder.parseAddress("{\"status\":311,\"message\":\"Key格式错误\"}"));
        assertNull(TencentGeocoder.parseAddress("{\"status\":110,\"result\":{\"address\":\"不可显示\"}}"));
        assertNull(TencentGeocoder.parseAddress("{\"result\":{\"address\":\"不可显示\"}}"));
        assertNull(TencentGeocoder.parseAddress("{\"status\":0}"));
        assertNull(TencentGeocoder.parseAddress("{\"status\":0,\"result\":{}}"));
        assertNull(TencentGeocoder.parseAddress("{\"status\":0,\"result\":{\"address\":\" \"}}"));
    }

    @Test public void ignoresMalformedResponsesWithoutCrashing() {
        assertNull(TencentGeocoder.parseAddress("<html>Bad gateway</html>"));
        assertNull(TencentGeocoder.parseAddress("{\"status\":"));
        assertNull(TencentGeocoder.parseAddress("[]"));
        assertNull(TencentGeocoder.parseAddress("null"));
        assertNull(TencentGeocoder.parseAddress(""));
    }
}
