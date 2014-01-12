/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Niko Fink
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.ncoder.studipsync.data;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class URLUtils {
    private URLUtils() {
    }

    public static Map<String, String> extractUrlParameters(URL url) {
        return extractParameters(url.getQuery());
    }

    public static Map<String, String> extractUriParameters(URI uri) {
        return extractParameters(uri.getQuery());
    }

    public static Map<String, String> extractParameters(String query) {
        String[] split = query.split("&");
        HashMap<String, String> params = new HashMap<>();
        for (String param : split) {
            String[] tuple = param.split("=");
            params.put(tuple[0], tuple[1]);
        }
        return params;
    }

    public static URL setUrlParameters(URL base, Map<String, String> params) {
        try {
            return new URL(base.getProtocol(), base.getHost(), base.getPort(), base.getPath() + "?" + joinParameters(params) + "#" + base.getRef());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Illegal URL generated from " + base + " with params " + params, e);
        }
    }

    public static URI setUriParameters(URI base, Map<String, String> params) {
        try {
            return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), base.getPath(), joinParameters(params), base.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Illegal URI generated from " + base + " with params " + params, e);
        }
    }

    public static String joinParameters(Map<String, String> params) {
        StringBuilder bob = new StringBuilder();
        for (Entry<String, String> e : params.entrySet()) {
            bob.append("&");
            bob.append(e.getKey());
            bob.append("=");
            bob.append(e.getValue());
        }
        return bob.substring(1);
    }
}
