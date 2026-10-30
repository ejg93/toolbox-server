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
}
