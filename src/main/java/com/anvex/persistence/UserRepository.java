package com.anvex.persistence;

import org.slf4j.Logger;
import com.anvex.util.PasswordUtil;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    private final DatabaseManager dbManager;

    public UserRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, role, enabled FROM users WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username: {}", username, e);
        }
        return Optional.empty();
    }

    public Optional<User> findById(long id) {
        String sql = "SELECT id, username, password_hash, role, enabled FROM users WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by id: {}", id, e);
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        String sql = "SELECT id, username, password_hash, role, enabled FROM users ORDER BY id";
        List<User> users = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all users", e);
        }
        return users;
    }

    public List<User> findByRole(String role) {
        String sql = "SELECT id, username, password_hash, role, enabled FROM users WHERE role = ? ORDER BY id";
        List<User> users = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding users by role: {}", role, e);
        }
        return users;
    }

    public boolean verifyCredentials(String username, String password) {
        return findByUsername(username)
                .filter(User::isEnabled)
                .map(user -> PasswordUtil.verifyPassword(password, user.getPasswordHash()))
                .orElse(false);
    }

    public User createUser(String username, String passwordHash, String role) {
        String sql = "INSERT INTO users (username, password_hash, role, enabled) VALUES (?, ?, ?, TRUE)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.setString(3, role);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return new User(id, username, passwordHash, role, true);
                }
            }
        } catch (SQLException e) {
            logger.error("Error creating user: {}", username, e);
        }

        throw new RuntimeException("Failed to create user: " + username);
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getBoolean("enabled")
        );
    }
}