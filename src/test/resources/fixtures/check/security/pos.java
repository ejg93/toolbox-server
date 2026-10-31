/**
 * 수정일 수정자 수정내용
 */
package a;

public class Sec {
    String dbPassword = "p@ssw0rd!";

    void q(javax.servlet.http.HttpServletRequest request, org.springframework.ui.Model model, String id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = '" + id + "'";
        java.io.File f = new java.io.File(request.getParameter("path"));
        try { q(request, model, id); } catch (Exception e) { model.addAttribute("err", e.getMessage()); }
    }

    // 5-13 KISA 2차 — 한 줄에 한 규칙
    void sec2(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, Upload file) throws Exception {
        Runtime.getRuntime().exec(request.getParameter("cmd"));
        Object o = new ObjectInputStream(request.getInputStream()).readObject();
        new ScriptEngineManager().getEngineByName("js");
        URL u = new URL(request.getParameter("url"));
        response.sendRedirect(request.getParameter("next"));
        response.setHeader("X-Name", request.getParameter("name"));
        MessageDigest md = MessageDigest.getInstance("MD5");
        Cipher c = Cipher.getInstance("DES/ECB/PKCS5Padding");
        int r = new Random().nextInt();
        SecretKeySpec k = new SecretKeySpec("0123456789abcdef".getBytes(), "AES");
        String name = file.getOriginalFilename();
        Cipher d = Cipher.getInstance("AES");
        String rrn = "900101-1234567";
        String card = "1234-5678-9012-3456";
        String phone = "010-1234-5678";
        log.debug("pw={}", password);
        conn.createStatement().executeQuery(request.getParameter("q"));
        response.getWriter().print(request.getParameter("m"));
        ctx.search("ou=people", request.getParameter("f"), sc);
        xpath.evaluate(request.getParameter("x"), doc);
        request.getSession().setAttribute("u", request.getParameter("u"));
        cookie.setSecure(false);
    }

    // 설계 48 — 막지 않은 XML 팩토리(한 줄에 한 건)
    void xml() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        javax.xml.parsers.SAXParserFactory.newInstance();
        XMLInputFactory x = XMLInputFactory.newFactory();
        TransformerFactory t = TransformerFactory.newInstance();
        SchemaFactory sf = SchemaFactory.newInstance(W3C);
        XMLReader r = XMLReaderFactory.createXMLReader();
    }

    // 값이 false 인 SECURE_PROCESSING · 이름공간 설정은 막음이 아니다
    void xmlOff() throws Exception {
        DocumentBuilderFactory f2 = DocumentBuilderFactory.newInstance();
        f2.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        f2.setNamespaceAware(true);
    }

    // 설계 48 — 인증서·호스트 검증 끔(한 줄에 한 건, getAcceptedIssuers 는 안 센다)
    void tls(HttpsURLConnection conn) {
        conn.setHostnameVerifier((h, s) -> true);
        conn.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        conn.setHostnameVerifier(new NoopHostnameVerifier());
        X509TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) { }
            public void checkServerTrusted(X509Certificate[] c, String a) { return; }
            public X509Certificate[] getAcceptedIssuers() { return null; }
        };
        HostnameVerifier hv = new HostnameVerifier() {
            public boolean verify(String h, SSLSession s) { return true; }
        };
    }
}
