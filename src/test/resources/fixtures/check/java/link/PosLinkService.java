package egov.pos.link;

public interface PosLinkService {
    void download(String name);

    void query(String q);

    void open(String pw) throws Exception;
}
