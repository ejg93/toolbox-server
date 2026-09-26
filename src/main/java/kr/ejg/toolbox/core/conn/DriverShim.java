package kr.ejg.toolbox.core.conn;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * DriverManager 는 자기 클래스로더가 못 보는 드라이버를 거절한다. `drivers/` 폴더 jar 를 URLClassLoader 로 연 드라이버를
 * 이 껍데기로 감싸 등록한다(1-4, 11장 drivers).
 */
final class DriverShim implements Driver {

    private final Driver driver;

    DriverShim(Driver driver) {
        this.driver = driver;
    }

    String driverClassName() {
        return driver.getClass().getName();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return driver.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return driver.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return driver.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return driver.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return driver.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return driver.getParentLogger();
    }
}
