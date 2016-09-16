package ru.nukkit.welcome.provider.simpleauth;

import cn.nukkit.utils.Config;
import org.sql2o.Connection;
import org.sql2o.Sql2o;
import ru.nukkit.welcome.Welcome;
import ru.nukkit.welcome.password.HashType;
import ru.nukkit.welcome.provider.PasswordProvider;
import ru.nukkit.welcome.provider.Providers;
import ru.nukkit.welcome.util.Message;

import java.io.File;
import java.util.List;

public class SimpleauthProvider implements PasswordProvider {

    private String createPlayersTable = "CREATE TABLE IF NOT EXISTS :table (name VARCHAR(200) NOT NULL, " +
            "hash VARCHAR(200), lastip VARCHAR(200), registerdate VARCHAR(200), logindate VARCHAR(200), PRIMARY KEY(name))";
    private String selectByName = "SELECT * FROM :table WHERE name = :name";

    private String selectByIp = "SELECT * FROM :table WHERE lastip = :lastip";


    // public final static String setPassword = "REPLACE INTO :table VALUES (:name, :hash, :lastip, :registerdate, :logindate)";

    private String updatePassword = "UPDATE :table SET name = :name, hash = :hash WHERE name = :name";
    private String updatePassword2 = "INSERT OR IGNORE INTO :table (name, hash, registerdate) VALUES (:name, :hash, :registerdate)";

    private String deletePlayer = "DELETE FROM :table WHERE name = :name";

    private String updateLastlogin = "UPDATE :table SET lastip = :lastip, logindate = :logindate WHERE name = :name";

    private String deleteLastlogin = "DELETE FROM :table WHERE name = :name";

    /*
    -- Try to update any existing row
UPDATE players SET user_name='steven', age=32 WHERE user_name='steven';

-- Make sure it exists
INSERT OR IGNORE INTO players (user_name, age) VALUES ('steven', 32);
     */

    private Sql2o sql2o;
    private boolean enabled;
    private String tableName;

    public SimpleauthProvider() {
        enabled = false;

        File oldCfgFile = new File(Welcome.getPlugin().getDataFolder() + File.separator + "simpleauth.yml");
        if (oldCfgFile.exists()) {
            Config oldCfg = new Config(oldCfgFile, Config.YAML);
            String oldTableName = oldCfg.getString("database.table-name");
            if (oldTableName != null && !oldTableName.isEmpty() && !oldTableName.equals(Welcome.getCfg().sbSimpleAuthTable)) {
                Welcome.getCfg().sbSimpleAuthTable = oldTableName;
                Welcome.getCfg().save();
            }
            oldCfgFile.delete();
            Message.OLD_FILE_REMOVED.log("NOCOLOR");
        }

        this.tableName = Welcome.getCfg().sbSimpleAuthTable;

        createPlayersTable = createPlayersTable.replace(":table", tableName);
        selectByName = selectByName.replace(":table", tableName);
        selectByIp = selectByIp.replace(":table", tableName);
        updatePassword = updatePassword.replace(":table", tableName);
        updatePassword2 = updatePassword2.replace(":table", tableName);
        deletePlayer = deletePlayer.replace(":table", tableName);
        updateLastlogin = updateLastlogin.replace(":table", tableName);
        deleteLastlogin = deleteLastlogin.replace(":table", tableName);


        sql2o = Providers.getSql2o();
        if (sql2o == null) return;

        try (Connection con = sql2o.open()) {
            con.createQuery(createPlayersTable)
                    //.addParameter("table", tableName)
                    .executeUpdate();
        }

        if (Welcome.getCfg().getHashAlgorithm() != HashType.SIMPLEAUTH)
            Message.DB_HASH_WARNING.log(Welcome.getCfg().getHashAlgorithm().name(), HashType.SIMPLEAUTH.name());

        enabled = true;
    }


    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean checkPassword(String playerName, String password) {
        if (!enabled) return false;
        if (playerName == null || playerName.isEmpty()) return false;
        PlayersTable pt;
        try (Connection con = sql2o.open()) {
            pt = con.createQuery(selectByName)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .executeAndFetchFirst(PlayersTable.class);
        }
        if (pt == null || pt.name == null || pt.hash.isEmpty()) return false;
        return password.equals(pt.hash);
    }

    @Override
    public boolean setPassword(String playerName, String password) {
        if (!enabled) return false;
        if (playerName == null || playerName.isEmpty()) return false;
        if (password == null || password.isEmpty()) return false;
        try (Connection con = sql2o.beginTransaction(java.sql.Connection.TRANSACTION_SERIALIZABLE)) {
            con.createQuery(updatePassword)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .addParameter("hash", password)
                    .executeUpdate();
            con.createQuery(updatePassword2)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .addParameter("hash", password)
                    .addParameter("registerdate", String.valueOf(System.currentTimeMillis()))
                    .executeUpdate();
            con.commit();
        }
        return true;
    }

    @Override
    public boolean hasPassword(String playerName) {
        if (!enabled) return false;
        if (playerName == null || playerName.isEmpty()) return false;
        PlayersTable pt;
        try (Connection con = sql2o.open()) {
            pt = con.createQuery(selectByName)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .executeAndFetchFirst(PlayersTable.class);
        }
        if (pt == null || pt.hash == null || pt.hash.isEmpty()) return false;
        return true;
    }

    @Override
    public boolean removePassword(String playerName) {
        if (!enabled) return false;
        if (playerName == null || playerName.isEmpty()) return false;
        try (Connection con = sql2o.open()) {
            con.createQuery(deletePlayer)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .executeUpdate();
        }
        return true;
    }

    @Override
    public Long lastLoginFromIp(String playerNane, String ip) {
        if (!enabled) return null; // Ошибка - регистрация запрещена
        List<PlayersTable> result;
        try (Connection con = sql2o.open()) {
            result = con.createQuery(selectByIp)
                    //.addParameter("table", tableName)
                    .addParameter("lastip", ip)
                    .executeAndFetch(PlayersTable.class);
        }
        if (result == null || result.isEmpty()) return null;
        long time = 0;
        for (PlayersTable row : result) {
            if (row.logindate == null || row.logindate.isEmpty()) continue;
            long lastTime = Long.parseLong(row.logindate);
            if (lastTime > time) time = lastTime;
        }
        return time;
    }

    @Override
    public boolean checkAutoLogin(String playerName, String uuid, String ip) {
        if (!enabled) return false;
        if (playerName == null || playerName.isEmpty()) return false;
        PlayersTable pt;
        try (Connection con = sql2o.open()) {
            pt = con.createQuery(selectByName)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .executeAndFetchFirst(PlayersTable.class);
        }
        if (pt == null) return false;
        if (pt.lastip == null || pt.lastip.isEmpty()) return false;
        if (pt.logindate == null || pt.logindate.isEmpty()) return false;

        if (System.currentTimeMillis() - Long.parseLong(pt.logindate) > Welcome.getCfg().getMaxAutoTime()) return false;
        return ip.equals(pt.lastip);
    }

    @Override
    public void updateAutoLogin(String playerName, String uuid, String ip, long currentTime) {
        if (!enabled) return;
        if (playerName == null || playerName.isEmpty()) return;
        try (Connection con = sql2o.open()) {
            con.createQuery(updateLastlogin)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .addParameter("lastip", ip)
                    .addParameter("logindate", String.valueOf(currentTime))
                    .executeUpdate();
        }
    }

    @Override
    public boolean removeAutoLogin(String playerName) {
        if (!enabled) return false;
        if (playerName == null || playerName.isEmpty()) return false;

        try (Connection con = sql2o.open()) {
            con.createQuery(deleteLastlogin)
                    //.addParameter("table", tableName)
                    .addParameter("name", playerName)
                    .executeUpdate();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void onDisable() {
        // Zzzzzz....
    }


}
