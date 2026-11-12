/**
 * 수정일 수정자 수정내용
 */
package a;

import org.codelibs.nekohtml.parsers.SAXParser;

public class SecNeg {
    String passwordPolicy = PolicyHolder.get();

    void q(org.springframework.ui.Model model, String id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";
        // String s = "SELECT " + id;
        java.io.File f = new java.io.File(BASE, safeName(id));
        try { q(model, id); } catch (Exception e) { LOG.warn("실패", e); model.addAttribute("err", "처리 실패"); }
    }

    // 5-13 KISA 2차 — 안전한 꼴. 주석 안은 안 센다: Runtime.getRuntime().exec(cmd); new Random();
    void sec2(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws Exception {
        SecureRandom r = new SecureRandom();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        response.sendRedirect("/fixed.do");
        URL u = new URL("https://fixed.example/api");
        response.addCookie(new Cookie("a", "b"));
        String key = KeyStoreHolder.load();
        // 예) 900101-1234567 · 010-1234-5678 — 주석은 안 센다
        String day = "2026-10-03";
        LOGGER.debug("{}.{}", className, methodName);
        log.error("Error creating password hash: {}", e2.getMessage());
        // 5-44a — 이어붙이기의 "password=" 는 값이 아니고, 설명·예외 글 속 SQL 낱말은 쿼리가 아니다
        String shown = "user=" + day + ", password=" + (key == null ? "null" : "****");
        // 설계 42 — 정화·상수 값은 오염이 아니다
        int n = Integer.parseInt(request.getParameter("n"));
        new java.io.File("f" + n);
        String fixed = "/home.do";
        response.sendRedirect(fixed);
        new java.net.URL(BASE + "/api");
        conn.createStatement().executeQuery("SELECT 1");
        response.getWriter().print("ok");
        ctx.search("ou=people", "(uid=admin)", sc);
        cookie.setSecure(true);
        request.getSession().setAttribute("u", userId);
        throw new IllegalArgumentException("method 는 GET·POST·PUT·DELETE: " + shown);
    }

    // 설계 48 — 같은 메서드나 같은 파일 도우미 한 홉에서 막은 XML 팩토리
    void xmlDoctype() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    }

    void xmlSax() throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    }

    void xmlSecure() throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }

    void xmlStax() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    void xmlTransform() {
        TransformerFactory f = TransformerFactory.newInstance();
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    }

    void xmlExpand() {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setExpandEntityReferences(false);
    }

    void xmlHop() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        harden(f);
    }

    void xmlSchema() throws Exception {
        SchemaFactory f = SchemaFactory.newInstance(W3C);
        f.setProperty(ACCESS_EXTERNAL_SCHEMA, "");
    }

    void xmlSecureVar(boolean on) throws Exception {
        TransformerFactory f = TransformerFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, on);
    }

    private void harden(DocumentBuilderFactory f) throws Exception {
        f.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
    }

    // 설계 48 — 검증하는 TLS · SSLSession 이 아닌 verify
    void tlsSafe(HttpsURLConnection conn, X509TrustManager delegate) {
        conn.setHostnameVerifier((h, s) -> h.equals(HOST));
        conn.setHostnameVerifier(new DefaultHostnameVerifier());
        X509TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException {
                delegate.checkClientTrusted(c, a);
            }
            public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException {
                if (c == null || c.length == 0) {
                    throw new CertificateException("empty");
                }
                delegate.checkServerTrusted(c, a);
            }
            public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
        };
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String h, SSLSession s) { return HOST.equals(h); }
        };
    }

    boolean verify(String code, String expected) {
        return true;
    }

    // R-13 — 빈 문서만 만드는 사슬은 파싱이 없다
    org.w3c.dom.Document empty() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    // 마무리 리뷰 — Boolean.FALSE 로 끔 · 필드로 넘긴 한 홉 · 변수에 담은 검증 람다
    void xmlBoxed() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    }

    void xmlField() throws Exception {
        this.fac = DocumentBuilderFactory.newInstance();
        harden(this.fac);
        HostnameVerifier hv2 = (h, s) -> HOST.equals(h);
    }
}

// 설계 50 — 자원 해제 음성: try-with-resources · finally close · closer 유틸 · return · 필드 · 래퍼 넘김 · 컬렉션 · 람다 ·
// 메모리 형식 · 형 변환 · 컨테이너 스트림 · 매개변수를 감싼 래퍼 · 다른 패키지 Statement
class SecLeakNeg {
    private Reader kept;
    private final java.util.List<Connection> pool = new java.util.ArrayList<>();

    void twr(String url) throws Exception {
        try (Connection c = DriverManager.getConnection(url)) {
            c.commit();
        }
    }

    void fin(java.io.File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            in.read();
        } finally {
            in.close();
            LOG.debug("closed");
        }
    }

    void quiet(java.io.File f) throws Exception {
        InputStream is = null;
        is = new FileInputStream(f);
        is.read();
        IOUtils.closeQuietly(is);
    }

    InputStream open(java.io.File f) throws Exception {
        InputStream is = new FileInputStream(f);
        return is;
    }

    void field(java.io.File f) throws Exception {
        Reader r = new FileReader(f);
        this.kept = r;
    }

    BufferedReader wrap(java.io.File f) throws Exception {
        FileReader fr = new FileReader(f);
        return new BufferedReader(fr);
    }

    void pooled(String url) throws Exception {
        Connection c = DriverManager.getConnection(url);
        pool.add(c);
    }

    Runnable later(java.io.File f) throws Exception {
        OutputStream os = new FileOutputStream(f);
        return () -> os.flush();
    }

    String memory() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pw.flush();
        return sw.toString() + bos.size();
    }

    void cast(Object obj, javax.servlet.http.HttpServletResponse response) throws Exception {
        Reader r = (Reader) obj;
        OutputStream out = response.getOutputStream();
        out.flush();
        r.read();
    }

    void copy(InputStream input, java.io.Writer output) throws Exception {
        InputStreamReader in = new InputStreamReader(input);
        in.read();
        Scanner sc = new Scanner(System.in);
        sc.next();
    }
}

// 설계 58 — static 블록이 막은 필드 · 팩토리 둘 다 막음 · dom4j·jdom 막음 · 메모리 DOM 만 쓰는 변환기
class SecXxeNeg58 {
    static final DocumentBuilderFactory NF = DocumentBuilderFactory.newInstance();

    static {
        NF.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    }

    void two() throws Exception {
        DocumentBuilderFactory g1 = DocumentBuilderFactory.newInstance();
        g1.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilderFactory g2 = DocumentBuilderFactory.newInstance();
        g2.setExpandEntityReferences(false);
    }

    void dom4j() throws Exception {
        org.dom4j.io.SAXReader r = new org.dom4j.io.SAXReader();
        r.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.jdom2.input.SAXBuilder b = new org.jdom2.input.SAXBuilder();
        b.setExpandEntities(false);
    }

    void dom(org.w3c.dom.Document doc, java.io.Writer w) throws Exception {
        DOMSource ds = new DOMSource(doc);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.transform(ds, new StreamResult(w));
    }

    void dom2(org.w3c.dom.Document doc, StreamResult out) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.newTransformer().transform(new DOMSource(doc), out);
    }
}

// 설계 58 — 자원 아닌 Stream · try-with-resources 의 Files 스트림 · 세션·요청·문맥이 준 것 · 필드·매개변수로 끝나는 지역 래퍼 사슬
class SecLeakNeg58 {
    private InputStream in;

    void streams(java.util.List<String> list, Path d) throws Exception {
        Stream<String> st = list.stream();
        st.count();
        try (Stream<Path> s = Files.walk(d)) {
            s.count();
        }
    }

    void owners(AtomRequest areq, TemplateContext ctx, SqlSession ss) throws Exception {
        Connection c = getSqlSession().getConnection();
        Connection c2 = ss.getConnection();
        InputStream in2 = areq.getInputStream();
        Writer w = ctx.getWriter();
        c.getMetaData();
        c2.getMetaData();
        in2.read();
        w.flush();
    }

    Object memory(byte[] bytes) throws Exception {
        ByteArrayInputStream bais = null;
        bais = new ByteArrayInputStream(bytes);
        BufferedInputStream bis = new BufferedInputStream(bais);
        return bis.read();
    }

    void chains(InputStream param) throws Exception {
        InputStream a = this.in;
        BufferedReader br = new BufferedReader(new InputStreamReader(a));
        br.readLine();
        InputStream b = param;
        Reader r = new InputStreamReader(b);
        r.read();
    }
}

// 5-65 — Cyberneko HTML 파서를 주는 SAXBuilder 는 DOCTYPE 외부 개체를 안 푼다
class SecNeko {
    void html() throws Exception {
        org.jdom2.input.SAXBuilder b = new org.jdom2.input.SAXBuilder(new org.jdom2.input.sax.XMLReaderSAX2Factory(true, SAXParser.class.getName()), null, null);
    }
}

// 설계 65 5-97 — Luhn 을 못 지나는 16 자리는 카드번호가 아니다
class SecSerial {
    String serial = "4030000010001235";
}
