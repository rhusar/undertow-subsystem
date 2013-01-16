package org.jboss.as.undertow.security;

import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import org.jboss.as.security.plugins.SecurityDomainContext;
import org.jboss.as.web.WebLogger;
import org.jboss.security.AuthenticationManager;
import org.jboss.security.AuthorizationManager;
import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityRolesAssociation;
import org.jboss.security.callbacks.SecurityContextCallbackHandler;
import org.jboss.security.identity.Role;
import org.jboss.security.identity.RoleGroup;
import org.jboss.security.mapping.MappingContext;
import org.jboss.security.mapping.MappingManager;
import org.jboss.security.mapping.MappingType;

/**
 * @author Stuart Douglas
 */
public class IdentityManagerImpl implements IdentityManager {

    private final SecurityDomainContext securityDomainContext;
    private final Map<String, Set<String>> principalVersusRolesMap;

    public IdentityManagerImpl(final SecurityDomainContext securityDomainContext, final Map<String, Set<String>> principalVersusRolesMap) {
        this.securityDomainContext = securityDomainContext;
        this.principalVersusRolesMap = principalVersusRolesMap;
    }

    @Override
    public Account lookupAccount(final String id) {
        return new AccountImpl(id);
    }

    @Override
    public Account verifyCredential(final Credential credential) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean verifyCredential(final Account account, final Credential credential) {
        final char[] credentials = ((PasswordCredential) credential).getPassword();
        final AuthenticationManager authenticationManager = securityDomainContext.getAuthenticationManager();
        final MappingManager mappingManager = securityDomainContext.getMappingManager();
        final AuthorizationManager authorizationManager = securityDomainContext.getAuthorizationManager();
        final SecurityContext sc = SecurityActions.getSecurityContext();
        final AccountImpl accountImpl = (AccountImpl) account;
        Principal incomingPrincipal = (Principal) account;
        Subject subject = new Subject();
        try {
            boolean isValid = authenticationManager.isValid(incomingPrincipal, credentials, subject);
            if (isValid) {
                WebLogger.WEB_SECURITY_LOGGER.tracef("User: " + incomingPrincipal + " is authenticated");
                if (sc == null)
                    throw new IllegalStateException("No SecurityContext found!");
                sc.getUtil().createSubjectInfo(incomingPrincipal, credentials, subject);
                SecurityContextCallbackHandler scb = new SecurityContextCallbackHandler(sc);
                if (mappingManager != null) {
                    // if there are mapping modules let them handle the role mapping
                    MappingContext<RoleGroup> mc = mappingManager.getMappingContext(MappingType.ROLE.name());
                    if (mc != null && mc.hasModules()) {
                        SecurityRolesAssociation.setSecurityRoles(principalVersusRolesMap);
                    }
                }
                RoleGroup roles = authorizationManager.getSubjectRoles(subject, scb);
                Set<String> roleSet = new HashSet<String>();
                for (Role role : roles.getRoles()) {
                    roleSet.add(role.getRoleName());
                }
                accountImpl.setRoles(roleSet);
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public char[] getPassword(final Account account) {
        return null;
    }

    @Override
    public boolean isUserInGroup(final Account account, final String group) {
        return ((AccountImpl) account).getRoles().contains(group);
    }
}