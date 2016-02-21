/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.server.security.auth.manager.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.manager.AbstractAuthenticationManager;
import org.apache.qpid.server.util.ConnectionBuilder;
import org.apache.qpid.server.util.ParameterizedTypes;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class OAuth2AuthenticationProviderImpl
        extends AbstractAuthenticationManager<OAuth2AuthenticationProviderImpl>
        implements OAuth2AuthenticationProvider<OAuth2AuthenticationProviderImpl>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2AuthenticationProviderImpl.class);
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private final ObjectMapper _objectMapper = new ObjectMapper();

    @ManagedAttributeField
    private URI _authorizationEndpointURI;

    @ManagedAttributeField
    private URI _tokenEndpointURI;

    @ManagedAttributeField
    private URI _identityResolverEndpointURI;

    @ManagedAttributeField
    private boolean _tokenEndpointNeedsAuth;

    @ManagedAttributeField
    private URI _postLogoutURI;

    @ManagedAttributeField
    private String _clientId;

    @ManagedAttributeField
    private String _clientSecret;

    @ManagedAttributeField
    private TrustStore _trustStore;

    @ManagedAttributeField
    private String _scope;

    @ManagedAttributeField
    private String _identityResolverType;

    private OAuth2IdentityResolverService _identityResolverService;

    private List<String> _tlsProtocolWhiteList;
    private List<String>  _tlsProtocolBlackList;

    private List<String> _tlsCipherSuiteWhiteList;
    private List<String> _tlsCipherSuiteBlackList;

    private int _connectTimeout;
    private int _readTimeout;


    @ManagedObjectFactoryConstructor
    protected OAuth2AuthenticationProviderImpl(final Map<String, Object> attributes,
                                               final Broker<?> broker)
    {
        super(attributes, broker);
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        String type = getIdentityResolverType();
        _identityResolverService = new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).get(type);
        _tlsProtocolWhiteList = getContextValue(List.class, ParameterizedTypes.LIST_OF_STRINGS, CommonProperties.QPID_SECURITY_TLS_PROTOCOL_WHITE_LIST);
        _tlsProtocolBlackList = getContextValue(List.class, ParameterizedTypes.LIST_OF_STRINGS, CommonProperties.QPID_SECURITY_TLS_PROTOCOL_BLACK_LIST);
        _tlsCipherSuiteWhiteList = getContextValue(List.class, ParameterizedTypes.LIST_OF_STRINGS, CommonProperties.QPID_SECURITY_TLS_CIPHER_SUITE_WHITE_LIST);
        _tlsCipherSuiteBlackList = getContextValue(List.class, ParameterizedTypes.LIST_OF_STRINGS, CommonProperties.QPID_SECURITY_TLS_CIPHER_SUITE_BLACK_LIST);
        _connectTimeout = getContextValue(Integer.class, AUTHENTICATION_OAUTH2_CONNECT_TIMEOUT);
        _readTimeout = getContextValue(Integer.class, AUTHENTICATION_OAUTH2_READ_TIMEOUT);
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        final OAuth2AuthenticationProvider<?> validationProxy = (OAuth2AuthenticationProvider<?>) proxyForValidation;
        validateResolver(validationProxy);
        validateSecureEndpoints(validationProxy);
        validatePostLogoutURI(validationProxy);
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        validateResolver(this);
        validateSecureEndpoints(this);
        validatePostLogoutURI(this);
    }

    private void validateSecureEndpoints(final OAuth2AuthenticationProvider<?> provider)
    {
        if (!"https".equals(provider.getAuthorizationEndpointURI().getScheme()))
        {
            throw new IllegalConfigurationException(String.format("Authorization endpoint is not secure: '%s'", provider.getAuthorizationEndpointURI()));
        }
        if (!"https".equals(provider.getTokenEndpointURI().getScheme()))
        {
            throw new IllegalConfigurationException(String.format("Token endpoint is not secure: '%s'", provider.getTokenEndpointURI()));
        }
        if (!"https".equals(provider.getIdentityResolverEndpointURI().getScheme()))
        {
            throw new IllegalConfigurationException(String.format("Identity resolver endpoint is not secure: '%s'", provider.getIdentityResolverEndpointURI()));
        }
    }

    private void validatePostLogoutURI(final OAuth2AuthenticationProvider<?> provider)
    {
        if (provider.getPostLogoutURI() != null)
        {
            String scheme = provider.getPostLogoutURI().getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme))
            {
                throw new IllegalConfigurationException(String.format("Post logout URI does not have a http or https scheme: '%s'", provider.getPostLogoutURI()));
            }
        }
    }

    private void validateResolver(final OAuth2AuthenticationProvider<?> provider)
    {
        final OAuth2IdentityResolverService identityResolverService =
                new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).get(provider.getIdentityResolverType());

        if(identityResolverService == null)
        {
            throw new IllegalConfigurationException("Unknown identity resolver " + provider.getType());
        }
        else
        {
            identityResolverService.validate(provider);
        }
    }

    @Override
    public List<String> getMechanisms()
    {
        return Collections.singletonList(OAuth2SaslServer.MECHANISM);
    }

    @Override
    public SaslServer createSaslServer(final String mechanism,
                                       final String localFQDN,
                                       final Principal externalPrincipal)
            throws SaslException
    {
        if(OAuth2SaslServer.MECHANISM.equals(mechanism))
        {
            return new OAuth2SaslServer();
        }
        else
        {
            throw new SaslException("Unknown mechanism: " + mechanism);
        }
    }

    @Override
    public AuthenticationResult authenticate(final SaslServer server, final byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
            {
                String accessToken = (String) server.getNegotiatedProperty(OAuth2SaslServer.ACCESS_TOKEN_PROPERTY);
                return authenticateViaAccessToken(accessToken);
            }
            else
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.CONTINUE);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    @Override
    public AuthenticationResult authenticateViaAuthorizationCode(final String authorizationCode, final String redirectUri)
    {
        URL tokenEndpoint;
        HttpURLConnection connection;
        byte[] body;
        try
        {
            tokenEndpoint = getTokenEndpointURI().toURL();


            ConnectionBuilder connectionBuilder = new ConnectionBuilder(tokenEndpoint);
            connectionBuilder.setConnectTimeout(_connectTimeout).setReadTimeout(_readTimeout);
            if (getTrustStore() != null)
            {
                try
                {
                    connectionBuilder.setTrustMangers(getTrustStore().getTrustManagers());
                }
                catch (GeneralSecurityException e)
                {
                    throw new ServerScopedRuntimeException("Cannot initialise TLS", e);
                }
            }
            connectionBuilder.setTlsProtocolWhiteList(getTlsProtocolWhiteList())
                    .setTlsProtocolBlackList(getTlsProtocolBlackList())
                    .setTlsCipherSuiteWhiteList(getTlsCipherSuiteWhiteList())
                    .setTlsCipherSuiteBlackList(getTlsCipherSuiteBlackList());
            LOGGER.debug("About to call token endpoint '{}'", tokenEndpoint);
            connection = connectionBuilder.build();

            connection.setDoOutput(true); // makes sure to use POST
            connection.setRequestProperty("Accept-Charset", UTF8);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + UTF8);
            connection.setRequestProperty("Accept", "application/json");

            if (getTokenEndpointNeedsAuth())
            {
                String encoded = DatatypeConverter.printBase64Binary((getClientId() + ":" + getClientSecret()).getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encoded);
            }

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("code", authorizationCode);
            requestBody.put("client_id", getClientId());
            requestBody.put("client_secret", getClientSecret());
            requestBody.put("redirect_uri", redirectUri);
            requestBody.put("grant_type", "authorization_code");
            requestBody.put("response_type", "token");
            body = OAuth2Utils.buildRequestQuery(requestBody).getBytes(UTF8);
            connection.connect();

            try (OutputStream output = connection.getOutputStream())
            {
                output.write(body);
            }

            try (InputStream input = OAuth2Utils.getResponseStream(connection))
            {
                final int responseCode = connection.getResponseCode();
                LOGGER.debug("Call to token endpoint '{}' complete, response code : {}", tokenEndpoint, responseCode);

                Map<String, Object> responseMap = _objectMapper.readValue(input, Map.class);
                if (responseCode != 200)
                {
                    IllegalStateException e = new IllegalStateException(String.format("Token endpoint failed, response code %d, error '%s', description '%s'",
                                                                                      responseCode,
                                                                                      responseMap.get("error"),
                                                                                      responseMap.get("error_description")));
                    LOGGER.error("Call to token endpoint failed", e);
                    return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
                }

                Object accessTokenObject = responseMap.get("access_token");
                if (accessTokenObject == null)
                {
                    IllegalStateException e = new IllegalStateException("Token endpoint response did not include 'access_token'");
                    LOGGER.error("Unexpected token endpoint response", e);
                    return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
                }
                String accessToken = String.valueOf(accessTokenObject);

                return authenticateViaAccessToken(accessToken);
            }
            catch (JsonProcessingException e)
            {
                IllegalStateException ise = new IllegalStateException(String.format("Token endpoint '%s' did not return json",
                                                                                    tokenEndpoint), e);
                LOGGER.error("Unexpected token endpoint response", e);
                return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, ise);
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Call to token endpoint failed", e);
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    @Override
    public AuthenticationResult authenticateViaAccessToken(String accessToken)
    {
        try
        {
            final Principal userPrincipal = _identityResolverService.getUserPrincipal(this, accessToken);
            OAuth2UserPrincipal oauthUserPrincipal = new OAuth2UserPrincipal(userPrincipal.getName(), accessToken);
            return new AuthenticationResult(oauthUserPrincipal);
        }
        catch (IOException | IdentityResolverException e)
        {
            LOGGER.error("Call to identity resolver failed", e);
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    @Override
    public URI getAuthorizationEndpointURI()
    {
        return _authorizationEndpointURI;
    }

    @Override
    public URI getTokenEndpointURI()
    {
        return _tokenEndpointURI;
    }

    @Override
    public URI getIdentityResolverEndpointURI()
    {
        return _identityResolverEndpointURI;
    }

    @Override
    public URI getPostLogoutURI()
    {
        return _postLogoutURI;
    }

    @Override
    public boolean getTokenEndpointNeedsAuth()
    {
        return _tokenEndpointNeedsAuth;
    }

    @Override
    public String getIdentityResolverType()
    {
        return _identityResolverType;
    }

    @Override
    public String getClientId()
    {
        return _clientId;
    }

    @Override
    public String getClientSecret()
    {
        return _clientSecret;
    }

    @Override
    public TrustStore getTrustStore()
    {
        return _trustStore;
    }

    @Override
    public String getScope()
    {
        return _scope;
    }

    @Override
    public URI getDefaultAuthorizationEndpointURI()
    {
        final OAuth2IdentityResolverService identityResolverService =
                new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).get(getIdentityResolverType());
        return identityResolverService == null ? null : identityResolverService.getDefaultAuthorizationEndpointURI(this);
    }

    @Override
    public URI getDefaultTokenEndpointURI()
    {
        final OAuth2IdentityResolverService identityResolverService =
                new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).get(getIdentityResolverType());
        return identityResolverService == null ? null : identityResolverService.getDefaultTokenEndpointURI(this);
    }

    @Override
    public URI getDefaultIdentityResolverEndpointURI()
    {
        final OAuth2IdentityResolverService identityResolverService =
                new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).get(getIdentityResolverType());
        return identityResolverService == null ? null : identityResolverService.getDefaultIdentityResolverEndpointURI(this);
    }

    @Override
    public String getDefaultScope()
    {
        final OAuth2IdentityResolverService identityResolverService =
                new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).get(getIdentityResolverType());
        return identityResolverService == null ? null : identityResolverService.getDefaultScope(this);    }

    @Override
    public List<String> getTlsProtocolWhiteList()
    {
        return _tlsProtocolWhiteList;
    }

    @Override
    public List<String> getTlsProtocolBlackList()
    {
        return _tlsProtocolBlackList;
    }

    @Override
    public List<String> getTlsCipherSuiteWhiteList()
    {
        return _tlsCipherSuiteWhiteList;
    }

    @Override
    public List<String> getTlsCipherSuiteBlackList()
    {
        return _tlsCipherSuiteBlackList;
    }

    @Override
    public int getConnectTimeout()
    {
        return _connectTimeout;
    }

    @Override
    public int getReadTimeout()
    {
        return _readTimeout;
    }

    @SuppressWarnings("unused")
    public static Collection<String> validIdentityResolvers()
    {
        return new QpidServiceLoader().getInstancesByType(OAuth2IdentityResolverService.class).keySet();
    }
}