package org.powerbot.util;

import org.powerbot.bot.*;
import org.powerbot.script.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class HttpUtils {
	private static final String USER_AGENT;

	static {
		final boolean x64 = System.getProperty("sun.arch.data.model").equals("64");
		final StringBuilder s = new StringBuilder(60);

		s.append(ContextClassLoader.class.getAnnotation(Script.Manifest.class).name()).append('/').append(ContextClassLoader.VERSION).append(" (");
		final String os = System.getProperty("os.name", "");
		if (os.contains("Mac")) {
			s.append("Macintosh; Intel ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version").replace('.', '_'));
		} else if (os.contains("Linux")) {
			s.append("X11; Linux ").append(x64 ? "x86_64" : "i686");
		} else {
			s.append("Windows NT ").append(System.getProperty("os.version"));
			if (x64) {
				s.append("; WOW64");
			}
		}
		s.append(") Java/").append(System.getProperty("java.version"));

		USER_AGENT = s.toString();
	}

	public static HttpURLConnection openConnection(final URL url) throws IOException {
		final HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.addRequestProperty("Host", url.getHost());
		con.addRequestProperty("Connection", "close");
		if (!("." + url.getHost()).endsWith(".runescape.com")) {
			con.addRequestProperty("User-Agent", USER_AGENT);
			con.addRequestProperty("Accept-Encoding", "gzip,deflate");
			con.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
			con.addRequestProperty("Accept", "text/plain,image/*,application/java-archive;q=0.9,*/*;q=0.8");
		}
		con.setConnectTimeout(10000);
		return con;
	}

	public static HttpURLConnection openConnection(HttpURLConnection con) throws IOException {
		final Map<String, List<String>> props = new HashMap<>();

		while (true) {
			final String method, location = con.getHeaderField("Location");
			if (location == null || location.isEmpty()) {
				return con;
			}
			props.putAll(con.getRequestProperties());

			switch (con.getResponseCode()) {
			case HttpURLConnection.HTTP_MOVED_PERM:
			case HttpURLConnection.HTTP_MOVED_TEMP:
				method = "GET";
				break;

			case 307:
			case 308:
				method = con.getRequestMethod();
				break;

			default:
				return con;
			}

			con = (HttpURLConnection) new URL(con.getURL(), location).openConnection();
			con.setRequestMethod(method);
			for (final Map.Entry<String, List<String>> k : props.entrySet()) {
				for (final String v : k.getValue()) {
					con.addRequestProperty(k.getKey(), v);
				}
			}
			props.clear();
		}
	}

	public static HttpURLConnection download(final URL url, final File file) throws IOException {
		return download(openConnection(url), file);
	}

	private static HttpURLConnection download(final HttpURLConnection con, final File file) throws IOException {
		if (file.exists()) {
			try {
				con.setIfModifiedSince(file.lastModified());
			} catch (final IllegalStateException ignored) {
			}
		}

		switch (con.getResponseCode()) {
		case HttpURLConnection.HTTP_OK:
			try (final InputStream in = openStream(con); final OutputStream out = new FileOutputStream(file)) {
				final byte[] b = new byte[8192];
				int l;
				while ((l = in.read(b)) != -1) {
					out.write(b, 0, l);
				}
			}
			break;
		case HttpURLConnection.HTTP_NOT_FOUND:
		case HttpURLConnection.HTTP_GONE:
			if (file.exists()) {
				//noinspection ResultOfMethodCallIgnored
				file.delete();
			}
			break;
		}

		con.disconnect();
		return con;
	}

	public static InputStream openStream(final URL url) throws IOException {
		return openStream(openConnection(url));
	}

	public static InputStream openStream(final URLConnection con) throws IOException {
		InputStream in;
		try {
			in = con.getInputStream();
		} catch (final FileNotFoundException e) {
			if (con instanceof HttpURLConnection) {
				in = ((HttpURLConnection) con).getErrorStream();
			} else {
				throw e;
			}
		}
		final String e = con.getHeaderField("Content-Encoding");
		if (e == null || e.isEmpty()) {
			return in;
		}
		if (e.equalsIgnoreCase("gzip")) {
			return new GZIPInputStream(in);
		} else if (e.equalsIgnoreCase("deflate")) {
			return new InflaterInputStream(in, new Inflater(true));
		}
		return in;
	}

	public static InputStreamReader openReader(final URLConnection con) throws IOException {
		Charset c = StandardCharsets.UTF_8;

		final String t = con.getHeaderField("Content-Type");
		if (t != null && !t.isEmpty()) {
			final Matcher r = Pattern.compile("\\bcharset\\s*=\\s*([^;]+)", Pattern.CASE_INSENSITIVE).matcher(t);
			if (r.find()) {
				try {
					c = Charset.forName(r.group(1));
				} catch (final IllegalArgumentException ignored) {
				}
			}
		}

		return new InputStreamReader(openStream(con), c);
	}

	public static long getExpires(final URLConnection con) {
		final long d = System.currentTimeMillis();
		final String c = con.getHeaderField("cache-control");
		if (c == null || c.isEmpty()) {
			return d;
		}
		final Pattern p = Pattern.compile("\\bmax-age\\s*=\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
		final Matcher m = p.matcher(c);
		if (m.find()) {
			final long l = Long.parseLong(m.group(1));
			return d + TimeUnit.SECONDS.toMillis(l);
		}

		final long exp = con.getExpiration();
		return exp == 0L ? d : exp;
	}
}
