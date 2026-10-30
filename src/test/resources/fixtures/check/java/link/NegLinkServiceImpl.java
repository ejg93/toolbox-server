package egov.neg.link;

public class NegLinkServiceImpl {
    public void download(String name) {
        new java.io.File("/data/" + name);
    }

    public void page(int n) {
        new java.io.File("/data/" + n);
    }
}
