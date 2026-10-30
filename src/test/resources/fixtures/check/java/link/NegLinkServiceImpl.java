package egov.neg.link;

public class NegLinkServiceImpl {
    public void download(String name) {
        new java.io.File("/data/" + name);
    }

    public void page(int n) {
        new java.io.File("/data/" + n);
    }

    public void digest(java.util.Properties props) throws Exception {
        java.security.MessageDigest.getInstance(props.getProperty("hash.alg", "MD5"));
    }
}
