/**
 * 수정일 수정자 수정내용
 */
package a;

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
}
