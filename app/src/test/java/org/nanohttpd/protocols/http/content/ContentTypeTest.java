package org.nanohttpd.protocols.http.content;

import org.junit.Test;

import static org.junit.Assert.*;


public class ContentTypeTest {
    @Test
    public void testContentType(){


        ContentType ct = new ContentType ("Fruit");
        assertEquals(ct.getContentTypeHeader(), "Fruit");
        assertEquals(ct.getBoundary(), null);
    }
}