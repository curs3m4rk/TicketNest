CREATE TABLE roles (
                       id UUID NOT NULL,
                       name VARCHAR(50) NOT NULL,
                       description VARCHAR(255),
                       system_role BOOLEAN NOT NULL,
                       CONSTRAINT pk_roles PRIMARY KEY (id),
                       CONSTRAINT uk_roles_name UNIQUE (name),
                       CONSTRAINT ck_roles_name CHECK (name ~ '^[A-Z][A-Z0-9_]{2,49}$')
    );

CREATE TABLE role_permissions (
                                  role_id UUID NOT NULL,
                                  permission VARCHAR(50) NOT NULL,
                                  CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission),
                                  CONSTRAINT ck_role_permissions_permission CHECK (permission IN ('VENUE_MANAGE', 'SHOW_MANAGE')),
                                  CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE user_roles (
                            user_id UUID NOT NULL,
                            role_id UUID NOT NULL,
                            CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

INSERT INTO roles (id, name, description, system_role) VALUES
                                                           ('00000000-0000-0000-0000-000000000001', 'USER', 'Default registered user', TRUE),
                                                           ('00000000-0000-0000-0000-000000000002', 'ADMIN', 'System administrator', TRUE);

INSERT INTO role_permissions (role_id, permission) VALUES
                                                       ('00000000-0000-0000-0000-000000000002', 'VENUE_MANAGE'),
                                                       ('00000000-0000-0000-0000-000000000002', 'SHOW_MANAGE');

INSERT INTO user_roles (user_id, role_id)
SELECT id, CASE role
               WHEN 'ADMIN' THEN '00000000-0000-0000-0000-000000000002'::UUID
               ELSE '00000000-0000-0000-0000-000000000001'::UUID
    END
FROM users;

ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users DROP COLUMN role;
