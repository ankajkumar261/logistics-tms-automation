package com.portfolio.logistics.model;

import com.portfolio.logistics.config.ConfigReader;

/**
 * Application login roles. Credentials live in config.properties (gitignored), never in source.
 * Corporate ID defaults to superadmin.corporateId; a role can override it with
 * role.<name>.corporateId (e.g. role.dispatcher.corporateId=TIQ00820).
 * Two password tiers exist in the QA tenant:
 * SUPERADMIN/ADMIN use one password, DISPATCHER/DRIVER/ACCOUNTS/REPORTS another.
 */
public enum Role {
    SUPERADMIN, ADMIN, DISPATCHER, DRIVER, ACCOUNTS, REPORTS;

    public String corporateId() {
        return this == SUPERADMIN
                ? ConfigReader.corporateId()
                : ConfigReader.get("role." + name().toLowerCase() + ".corporateId",
                        ConfigReader.corporateId());
    }

    public String username() {
        return this == SUPERADMIN
                ? ConfigReader.superAdminUsername()
                : ConfigReader.get("role." + name().toLowerCase() + ".username");
    }

    public String password() {
        return this == SUPERADMIN
                ? ConfigReader.superAdminPass()
                : ConfigReader.get("role." + name().toLowerCase() + ".password");
    }
}
