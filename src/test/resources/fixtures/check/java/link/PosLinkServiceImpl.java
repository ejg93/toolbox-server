package egov.pos.link;

public class PosLinkServiceImpl implements PosLinkService {
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public void download(String name) {
        new java.io.File("/data/" + name);
        query(name);
    }

    @Override
    public void query(String q) {
        jdbc.queryForList("SELECT * FROM t WHERE a = '" + q + "'");
    }

    public void runLast() throws Exception {
        Runtime.getRuntime().exec(PosLinkHolder.last);
    }

    public void encrypt(java.util.Properties props) throws Exception {
        javax.crypto.Cipher.getInstance(props.getProperty("crypto.alg", "AES/GCM/NoPadding"));
    }

    public void connect(String url) throws Exception {
        java.sql.DriverManager.getConnection(url, "sa", PosLinkConfig.DB_PW);
        new javax.crypto.spec.SecretKeySpec(PosLinkConfig.key().getBytes(), "AES");
    }

    @Override
    public void open(String pw) throws Exception {
        java.sql.DriverManager.getConnection("jdbc:x", "sa", pw);
    }
}
