package resource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 설계 50 — try-with-resources 고침: 꼴 B 하나 · 꼴 B 둘 · 꼴 A · null 지킴 + try/catch close · 줄 꼴 틀림 · 중첩 */
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
}
