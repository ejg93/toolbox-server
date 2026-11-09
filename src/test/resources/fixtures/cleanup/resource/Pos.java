package resource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 설계 50 — try-with-resources 고침: 꼴 B 하나 · 꼴 B 둘 · 꼴 A · null 지킴 + try/catch close · 줄 꼴 틀림 · 중첩 · 꼴 A' 하나·둘·한 줄 null 지킴·묶음 밖 close·블록 null 지킴(건너뜀) · close 뒤 몸 안 변수(5-68 — 건너뜀) */
public class Pos {

    int formB(String path) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(path);
            return is.read();
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    void copy(String from, String to) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            out.write(in.read());
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    int formA(String path) throws IOException {
        FileInputStream in = new FileInputStream(path);
        try {
            return in.read();
        } finally {
            in.close();
        }
    }

    int guarded(String path) {
        InputStream is = null;
        try {
            is = new FileInputStream(path);
            return is.read();
        } catch (IOException e) {
            return -1;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    int commented(String path) throws IOException {
        InputStream is = null;
        try { // 주석
            is = new FileInputStream(path);
            return is.read();
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    int nested(String a, String b) throws IOException {
        InputStream x = new FileInputStream(a);
        try {
            InputStream y = new FileInputStream(b);
            try {
                return x.read() + y.read();
            } finally {
                y.close();
            }
        } finally {
            x.close();
        }
    }

    int formC(String path) {
        int n = -1;
        InputStream is = null;
        try {
            is = new FileInputStream(path);
            n = is.read();
            is.close();
        } catch (IOException e) {
            n = -2;
        }
        return n;
    }

    void formAp(String path, byte[] b) throws IOException {
        OutputStream out = new FileOutputStream(path);
        out.write(b);
        out.close();
    }

    void formApTwo(String a, String b) throws IOException {
        InputStream in = new FileInputStream(a);
        OutputStream out = new FileOutputStream(b);
        out.write(in.read());
        in.close();
        out.close();
    }

    void formApGuarded(String path, byte[] b) throws IOException {
        OutputStream out = new FileOutputStream(path);
        out.write(b);
        if (out != null) out.close();
    }

    void formApMixed(String a, String b, InputStream other) throws IOException {
        InputStream in = new FileInputStream(a);
        OutputStream out = new FileOutputStream(b);
        out.write(in.read() + other.read());
        in.close();
        other.close();
        out.close();
    }

    void formApBlockGuard(String path, byte[] b) throws IOException {
        OutputStream out = new FileOutputStream(path);
        out.write(b);
        if (out != null) {
            out.close();
        }
    }

    int formHoist(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        int n = is.read();
        is.close();
        return n;
    }
}
