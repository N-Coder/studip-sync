package de.ncoder.studipsync.studip.jsoup;

import de.ncoder.studipsync.data.LoginData;
import de.ncoder.studipsync.data.Seminar;
import de.ncoder.studipsync.data.StudipFile;
import de.ncoder.studipsync.studip.StudipAdapter;
import de.ncoder.studipsync.studip.StudipException;
import de.ncoder.studipsync.ui.UIAdapter;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsoupStudipAdapter implements StudipAdapter {
	public static final String PAGE_BASE = "https://studip.uni-passau.de/studip/";

	public static final String PAGE_LOGIN = PAGE_BASE + "index.php?again=yes";
	public static final String PAGE_DO_LOGIN = PAGE_LOGIN;

	public static final String PAGE_SEMINARS = PAGE_BASE + "meine_seminare.php";

	public static final String PAGE_DOWNLOADS = PAGE_BASE + "folder.php?cid=%s";
	public static final String PAGE_DOWNLOADS_ALL = PAGE_DOWNLOADS + "&cmd=all";
	public static final String PAGE_DOWNLOADS_TREE = PAGE_DOWNLOADS + "&cmd=tree";
	public static final String PAGE_DOWNLOAD_BODY = PAGE_DOWNLOADS + "&open=%s&data%%5Bcmd%%5D=tree";

	public static final String PAGE_DOWNLOAD_SEMINAR_ALL = PAGE_DOWNLOADS + "&zipnewest=-1&data%%5Bcmd%%5D=tree";
	public static final String PAGE_DOWNLOAD_SEMINAR_NEWEST = PAGE_DOWNLOADS + "&zipnewest=%d&data%%5Bcmd%%5D=tree";

	public static final String PAGE_DOWNLOAD_FOLDER = PAGE_DOWNLOADS + "&folderzip=%s&data%%5Bcmd%%5D=tree";
	public static final String PAGE_DOWNLOAD_FILE = PAGE_BASE + "sendfile.php?type=0&force_download=1&file_id=%s&file_name=studip-sync.tmp";

	public static final String[] URI_ILLEGAL_CHARS = new String[]{" ", "ä", "ö", "ü", "Ä", "Ö", "Ü", "ß", ":", "(", ")", "/", "\\"};
	public static final String[] URI_REPLACE_CHARS = new String[]{"_", "ae", "oe", "ue", "Ae", "Oe", "Ue", "ss", "", "", "", "", ""};
	//StudIP:
	//$bad_characters = array (":", chr(92), "/", "\"", ">", "<", "*", "|", "?", " ", "(", ")", "&", "[", "]", "#", chr(36), "'", "*", ";", "^", "`", "{", "}", "|", "~", chr(255));
	//$replacements = array ("", "", "", "", "", "", "", "", "", "_", "", "", "+", "", "", "", "", "", "", "-", "", "", "", "", "-", "", "");

	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy - HH:mm"); // 26.08.2013 - 20:38

	public static final Pattern MD5_PATTERN = Pattern.compile("getmd5_(fo|fi)([0-9a-f]+)_?(.*)");
	public static final Pattern FOLDER_INFO_PATTERN = Pattern.compile("\\(([0-9]+) ?[A-Za-z]*\\)");
	public static final Pattern FILE_INFO_PATTERN = Pattern.compile("\\(([0-9]+) ?([A-Za-z]?)B ?/ ?([0-9]+) ?[A-Za-z]*\\)");
	public static final Pattern ZIPNEWEST_PATTERN = Pattern.compile("zipnewest=([0-9]+)");

	// ------------------------------------------------------------------------

	private static final Logger log = LoggerFactory.getLogger(JsoupStudipAdapter.class);

	private final UIAdapter ui;
	private final Path cookiesPath;
	private final int timeoutMs;

	public JsoupStudipAdapter(UIAdapter ui, Path cookiesPath, int timeoutMs) {
		this.ui = ui;
		this.cookiesPath = cookiesPath;
		this.timeoutMs = timeoutMs;
	}

	// --------------------------------BROWSER---------------------------------

	private HttpConnection con;
	private Document document;
	private final List<NavigationListener> listeners = new ArrayList<>();

	private void setDocument(Document document) throws StudipException {
		this.document = document;
		try {
			URL url = new URL(document.baseUri());
			log.trace("NAV: " + url);
			for (NavigationListener listener : listeners) {
				listener.navigated(url);
			}
		} catch (MalformedURLException e) {
			StudipException ex = new StudipException("Illegal URL " + document.baseUri(), e);
			ex.put("studip.url", document.baseUri());
			ex.put("studip.document", document);
			throw ex;
		}
	}

	public Document getDocument() {
		return document;
	}

	protected void navigate(String url) throws StudipException {
		try {
			con.url(url);
			con.timeout(timeoutMs);
			try {
				setDocument(con.get());
			} catch (IOException e) {
				throw new StudipException("Can't navigate to " + url, e);
			}
		} catch (StudipException ex) {
			ex.put("navigate.url", url);
			ex.put("navigate.connection", con);
			ex.put("navigate.document", document);
			throw ex;
		}
	}

	@Override
	public InputStream startDownload(String urlString) throws IOException, StudipException {
		try {
			URL url = new URL(urlString);
			URLConnection urlCon = url.openConnection();
			if (!(urlCon instanceof HttpURLConnection)) {
				StudipException ex = new StudipException(new IllegalArgumentException("Can only download via http"));
				ex.put("download.url", url);
				ex.put("download.urlConnection", urlCon);
				throw ex;
			}
			HttpURLConnection con = (HttpURLConnection) urlCon;
			con.setRequestProperty("Cookie", HttpConnection.Response.getRequestCookieString(this.con.request().cookies()));
			return con.getInputStream();
		} catch (StudipException ex) {
			ex.put("download.download", urlString);
			throw ex;
		}
	}

	public void displayWebsite() {
		try {
			Path tmp = Files.createTempFile("studip-dump", ".html");
			Files.copy(new ByteArrayInputStream(document.outerHtml().getBytes()), tmp, StandardCopyOption.REPLACE_EXISTING);
			log.info("Displaying " + con.url());
			ui.displayWebpage(tmp.toUri());
		} catch (IOException e) {
			log.warn("Can't write page dump", e);
		}
	}

	// --------------------------------LIFECYCLE-------------------------------

	@Override
	public void init() throws StudipException {
		con = new HttpConnection();
		navigate(PAGE_BASE);
	}

	@Override
	public void close() throws IOException {
		ui.close();
	}

	// --------------------------------LOG IN----------------------------------

	@Override
	public boolean doLogin() throws CancellationException, StudipException {
		try {
			navigate(PAGE_LOGIN);
			if (hasCookies()) {
				restoreCookies();
				navigate(PAGE_BASE);
			} else {
				log.info("Requesting login data.");
				LoginData login = ui.requestLoginData();
				if (login != null) {
					doLogin(login);
					login.clean();
				} else {
					throw new CancellationException("Login cancelled by user");
				}
			}

			if (isLoggedIn()) {
				saveCookies();
				return true;
			} else {
				deleteCookies();
				return false;
			}
		} catch (IOException e) {
			StudipException ex = new StudipException(e);
			ex.put("studip.cookiesPath", cookiesPath);
			ex.put("studip.url", document == null ? "none" : document.baseUri());
			throw ex;
		}
	}

	protected void doLogin(LoginData login) throws StudipException {
		try {
			con.url(PAGE_DO_LOGIN);
			con.method(Connection.Method.POST);
			con.timeout(timeoutMs);

			con.data("security_token", document.getElementsByAttributeValue("name", "security_token").val());
			con.data("login_ticket", document.getElementsByAttributeValue("name", "login_ticket").val());

			con.data("device_pixel_ratio", "1");
			con.data("resolution", "1000x1000");

			con.data("loginname", login.getUsername());
			con.data("password", new String(login.getPassword()));
			con.cookies(con.response().cookies());
			login.clean();
			try {
				//FIXME check for password leaks
				setDocument(con.post());
			} catch (IOException e) {
				throw new StudipException("Can't login", e);
			} finally {
				con.data("password", "XXX");
				login.clean();
			}
		} catch (StudipException ex) {
			ex.put("navigate.url", con == null || con.request() == null ? "none" : con.request().url());
			ex.put("navigate.connection", con);
			ex.put("navigate.document", document);
			throw ex;
		}
	}

	@Override
	public boolean isLoggedIn() {
		Elements selected = document.select("#footer");
		return selected.size() == 1 && selected.get(0).text().contains("angemeldet");
	}

	public void ensureLoggedIn() throws StudipException {
		while (!isLoggedIn()) {
			doLogin();
		}
	}

	// --------------------------------COOKIES---------------------------------

	public void saveCookies() throws IOException {
		if (cookiesPath != null) {
			log.info("Save Cookies");
			if (Files.exists(cookiesPath)) {
				Files.delete(cookiesPath);
			}
			try (Writer w = Files.newBufferedWriter(cookiesPath, Charset.defaultCharset())) {
				w.write(JSONValue.toJSONString(con.request().cookies()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void restoreCookies() throws IOException {
		if (cookiesPath != null) {
			log.info("Restore Cookies");
			try (Reader r = Files.newBufferedReader(cookiesPath, Charset.defaultCharset())) {
				Object o = new JSONParser().parse(r);
				con.request().cookies().putAll((Map) o);
			} catch (org.json.simple.parser.ParseException | ClassCastException e) {
				throw new IOException("Illegal data in cookies file " + cookiesPath, e);
			}
		}
	}

	public void deleteCookies() throws IOException {
		if (cookiesPath != null && Files.exists(cookiesPath)) {
			Files.delete(cookiesPath);
		}
	}

	public boolean hasCookies() {
		try {
			return cookiesPath != null && Files.isRegularFile(cookiesPath) && Files.size(cookiesPath) > 0;
		} catch (IOException e) {
			log.warn("Couldn't read cookies from " + cookiesPath + ", prompting for password", e);
			return false;
		}
	}

	// --------------------------------SEMINARS--------------------------------

	private boolean seminarsLoaded = false;
	private final List<Seminar> seminars = new ArrayList<>();
	private final List<Seminar> seminarsExt = Collections.unmodifiableList(seminars);

	@Override
	public List<Seminar> getSeminars() throws StudipException {
		if (!seminarsLoaded) {
			loadSeminars();
			seminarsLoaded = true;
		}
		return seminarsExt;
	}

	@Override
	public Seminar getSeminar(String hash) throws StudipException {
		for (Seminar seminar : getSeminars()) {
			if (seminar.getHash().equals(hash)) {
				return seminar;
			}
		}
		return null;
	}

	private Seminar addSeminar(String hash, String id, String name, String period) throws StudipException {
		Seminar seminar = null;
		if (seminarsLoaded) {
			seminar = getSeminar(hash);
		}
		if (seminar == null) {
			seminar = new JsoupSeminar(hash, id, name, period);
			seminars.add(seminar);
		}
		return seminar;
	}

	// --------------------------------PARSERS---------------------------------

	private void loadSeminars() throws StudipException {
		ensureLoggedIn();
		navigate(PAGE_SEMINARS);

		Elements semesters = document.select("#my_seminars .toggleable");
		for (Element semester : semesters) {
			Elements rows = semester.select("tr");
			String period = trim(rows.get(0).select("a.tree").text());
			rows.remove(0);
			for (Element row : rows) {
				Elements cols = row.select("td");
				String hash = extractHash(cols.select("a").get(0).attr("href"));
				String id = trim(cols.get(2).text());
				String name = trim(cols.get(3).text());
				addSeminar(hash, id, name, period);
			}
		}

		seminarsLoaded = true;
		log.debug("Parsed " + seminars.size() + " seminars.");
		log.trace(seminars.toString());
	}

	private Set<StudipFile> getDownloads(Seminar seminar) throws StudipException {
		ensureLoggedIn();
		navigate(String.format(PAGE_DOWNLOADS_TREE, seminar.getHash()));

		Set<StudipFile> files = new HashSet<>();
		for (Element folder : document.select("#folder_subfolders_root").first().children()) {
			parseFile(seminar, folder, files);
		}

		log.debug("Parsed " + files.size() + " downloads.");
		//log.trace(files.toString());
		return files;
	}

	private Date getLastSyncTime(Seminar seminar) throws StudipException {
		ensureLoggedIn();
		navigate(String.format(PAGE_DOWNLOADS_TREE, seminar.getHash()));

		Element newest = document.select("p.info a.button[href*=zipnewest]").first();
		if (newest != null) {
			Matcher m = ZIPNEWEST_PATTERN.matcher(newest.text());
			if (m.find()) {
				return new Date(TimeUnit.SECONDS.toMillis(Long.parseLong(m.group(1))));
			}
		}
		return null;
	}

	private void parseFile(Seminar seminar, Element root, Set<StudipFile> files) throws StudipException {
		if (root == null) {
			return;
		}

		StudipFile file = parseFileDescriptor(seminar, root);
		parseFileInfo(file, root);
		files.add(file);

		if (file.isFolder()) {
			Element body = findFileBody(file, root);

			Elements content = body.select(".folder_container > div");
			for (Element element : content) {
				parseFile(file.getSeminar(), element, files);
			}
		}
	}

	private StudipFile parseFileDescriptor(Seminar seminar, Element root) throws StudipException {
		Element getmd5 = root.select("> [id^=getmd5_]").get(0);
		String hash = trim(getmd5.text());

		Matcher m = MD5_PATTERN.matcher(getmd5.id().trim());
		if (!m.matches()) {
			throw new StudipException("Can't parse md5 id for file " + hash);
		}
		boolean isFolder = "fo".equals(m.group(1));
		String parentHash = trim(m.group(2));
		JsoupStudipFile file = new JsoupStudipFile(hash, seminar, isFolder, parentHash);
		file.body = root;
		return file;
	}

	private void parseFileInfo(StudipFile file, Element root) throws StudipException {
		Element header = root.select("#" + (file.isFolder() ? "folder" : "file") + "_" + file.getHash() + "_header").get(0);
		file.setName(trim(header.text()));
		file.setAuthor(trim(root.select("td[align=right].printhead a").text()));
		file.setLastModified(null);
		try {
			file.setLastModified(DATE_FORMAT.parse(
					trim(root.select("td[align=right].printhead").text().replace(file.getAuthor(), ""))
			));
		} catch (ParseException e) {
			throw new StudipException("Can't parse lastModified time", e);
		}
		String info = trim(header.parent().text().replace(file.getName(), ""));
		if (file.isFolder()) {
			Matcher m = FOLDER_INFO_PATTERN.matcher(info);
			if (m.matches()) {
				file.setSize(Integer.parseInt(m.group(1).trim()));
				//file.setDownloads(-1);
			}
		} else {
			Matcher m = FILE_INFO_PATTERN.matcher(info);
			if (m.matches()) {
				file.setSize(Long.parseLong(m.group(1).trim()) * siFactor(m.group(2).trim()));
				//file.setDownloads(Integer.parseInt(m.group(3).trim()));
			}
		}
		file.setChanged(
				file.getLastModified() != null && file.getSeminar().getLastSyncTime() != null
						&& file.getLastModified().after(file.getSeminar().getLastSyncTime())
		);
	}

	private Element findFileBody(StudipFile file, Element root) throws StudipException {
		Element table = null;
		if (root != null) {
			table = root.select("#" + (file.isFolder() ? "folder" : "file") + "_" + file.getHash() + "_body table").first();
		}
		if (table == null) {
			con.url(String.format(PAGE_DOWNLOAD_BODY, file.getSeminar().getHash(), file.getHash()));
			con.data("get" + (file.isFolder() ? "folder" : "file") + "body", file.getHash());
			con.header("X-Requested-With", "XMLHttpRequest");
			try {
				table = con.post();
			} catch (IOException e) {
				throw new StudipException("Can't load file information body", e);
			} finally {
				con.data("getfolderbody", "");
				con.header("X-Requested-With", "");
			}
		}
		return table;
	}

	// --------------------------------LISTENERS-------------------------------

	public static interface NavigationListener {
		public abstract void navigated(URL url);
	}

	public boolean addNavigationListener(NavigationListener navigationListener) {
		return listeners.add(navigationListener);
	}

	public boolean removeNavigationListener(NavigationListener o) {
		return listeners.remove(o);
	}

	// --------------------------------CLASSES---------------------------------

	private class JsoupStudipFile extends StudipFile {
		private final String parentHash;
		private Element body;

		public JsoupStudipFile(String hash, Seminar seminar, boolean isFolder, String parentHash) {
			super(hash, seminar, isFolder);
			this.parentHash = parentHash;
		}

		@Override
		public String getDownloadURL() {
			if (isFolder()) {
				return String.format(PAGE_DOWNLOAD_FOLDER, getHash());
			} else {
				return String.format(PAGE_DOWNLOAD_FILE, getHash());
			}
		}

		@Override
		public String getDownloadURL(long changesAfterTimestamp) {
			if (isFolder()) {
				//TODO
				return String.format(PAGE_DOWNLOAD_FOLDER, getHash());
			} else {
				return String.format(PAGE_DOWNLOAD_FILE, getHash());
			}
		}

		@Override
		protected void loadInfo() throws StudipException {
			for (StudipFile file : getSeminar().getFiles()) {
				if (file.getHash().equals(parentHash)) {
					parent = file;
					break;
				}
			}

			fileName = name;
			Element table = findFileBody(this, body);
			if (isFolder()) {
				Element info = table.select("td.printcontent[valign=bottom]").first();
				if (info != null && info.childNodeSize() > 4) {
					description = info.childNode(2).toString();
					fileName = info.childNode(4).toString();
				}
			} else {
				//TODO
			}
			for (int i = 0; i < URI_ILLEGAL_CHARS.length; i++) {
				fileName = fileName.replace(URI_ILLEGAL_CHARS[i], URI_REPLACE_CHARS[i]);
			}

			body = null;
		}
	}

	private class JsoupSeminar extends Seminar {
		public JsoupSeminar(String hash, String id, String name, String period) {
			super(hash, id, name, period);
		}

		@Override
		public String getDownloadURL() {
			return String.format(PAGE_DOWNLOAD_SEMINAR_ALL, getHash());
		}

		@Override
		public String getDownloadURL(long changesAfterTimestamp) {
			return String.format(PAGE_DOWNLOAD_SEMINAR_NEWEST, getHash(), changesAfterTimestamp);
		}

		@Override
		protected Iterable<StudipFile> loadFiles() throws StudipException {
			lastSync = JsoupStudipAdapter.this.getLastSyncTime(this);
			return JsoupStudipAdapter.this.getDownloads(this);
		}
	}

	// ------------------------------------------------------------------------

	private static String extractHash(String url) {
		return url.substring(url.indexOf("=") + 1);
	}

	private static String trim(String s) {
		int end = s.length();
		int start = 0;
		while ((start < end) && (s.charAt(start) <= ' ' || Character.isSpaceChar(s.charAt(start)))) {
			start++;
		}
		while ((start < end) && (s.charAt(end - 1) <= ' ' || Character.isSpaceChar(s.charAt(end - 1)))) {
			end--;
		}
		return ((start > 0) || (end < s.length())) ? s.substring(start, end) : s;
	}

	private static long siFactor(String suffix) {
		switch (suffix) {
			case "T":
				return 10 ^ 12;
			case "G":
				return 10 ^ 9;
			case "M":
				return 10 ^ 6;
			case "k":
				return 10 ^ 3;
			default:
				return 1;
		}
	}
}
