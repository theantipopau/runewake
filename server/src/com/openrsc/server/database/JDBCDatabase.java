package com.openrsc.server.database;

import com.openrsc.server.Server;
import com.openrsc.server.util.checked.CheckedConsumer;
import com.openrsc.server.util.checked.CheckedFunction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class JDBCDatabase extends GameDatabase {

    public JDBCDatabase(Server server) {
        super(server);
    }

    public abstract JDBCDatabaseConnection getConnection();

    public void withPreparedStatement(
            String query,
            CheckedConsumer<Exception, PreparedStatement> statementConsumer
    ) {
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            statementConsumer.accept(preparedStatement);
        } catch (Exception ex) {
            throw new GameDatabaseException(
                    getClass(),
                    ex.getMessage()
            );
        }
    }

    public <T> T withPreparedStatement(
            String query,
            CheckedFunction<Exception, PreparedStatement, T> statementFunction
    ) {
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            return statementFunction.apply(preparedStatement);
        } catch (Exception ex) {
            throw new GameDatabaseException(
                    getClass(),
                    ex.getMessage()
            );
        }
    }

    public void executeQuery(
            String query,
            CheckedConsumer<Exception, ResultSet> resultSetConsumer
    ) {
        // The PreparedStatement and ResultSet must stay open for the duration of the consumer
        // call - closing the statement (as the withPreparedStatement() helper does on return)
        // closes its ResultSet too per the JDBC spec, so routing through that helper handed
        // back an already-closed ResultSet here.
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            resultSetConsumer.accept(resultSet);
        } catch (Exception ex) {
            throw new GameDatabaseException(
                    getClass(),
                    ex.getMessage()
            );
        }
    }

    public PreparedStatement preparedStatement(String query) throws SQLException {
        return getConnection().prepareStatement(query);
    }
}
