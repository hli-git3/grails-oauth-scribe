package uk.co.desirableobjects.oauth.scribe

import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.scribe.model.Token
import org.scribe.model.Verifier

import uk.co.desirableobjects.oauth.scribe.exception.MissingRequestTokenException

class OauthController {

    private static final Token EMPTY_TOKEN = new Token('', '')

	def grailsApplication
    OauthService oauthService

    def callback = {

        String providerName = params.provider
        OauthProvider provider = oauthService.findProviderConfiguration(providerName)

        Verifier verifier = extractVerifier(provider, params)

        if (!verifier) {
            redirect(uri: provider.failureUri)
            return
        }

        Token requestToken = session[oauthService.findSessionKeyForRequestToken(providerName)]

        if (!requestToken) {
            throw new MissingRequestTokenException(providerName)
        }

        Token accessToken = oauthService.getAccessToken(providerName, requestToken, verifier)

        session[oauthService.findSessionKeyForAccessToken(providerName)] = accessToken
        session.removeAttribute(oauthService.findSessionKeyForRequestToken(providerName))

        return redirect(uri: provider.successUri)

    }

    private Verifier extractVerifier(OauthProvider provider, GrailsParameterMap params) {

        String verifierKey = determineVerifierKey(provider)

        if (!params[verifierKey]) {
             log.error("Cannot authenticate with oauth: Could not find oauth verifier in ${params}.")
             return null
        }

        String verification = params[verifierKey]
        return new Verifier(verification)

    }

    private String determineVerifierKey(OauthProvider provider) {

	    if("dropbox".equalsIgnoreCase(params.provider)) {
		    return 'oauth_token'
	    }

        return SupportedOauthVersion.TWO == provider.oauthVersion ? 'code' : 'oauth_verifier'

    }

    def authenticate = {

        String providerName = params.provider
        OauthProvider provider = oauthService.findProviderConfiguration(providerName)

        Token requestToken = EMPTY_TOKEN
	    Token reqToken
        if (provider.oauthVersion == SupportedOauthVersion.ONE) {
            requestToken = provider.service.requestToken

	        if(provider.callback) {
		         String callbackParam = "oauth_callback=" + provider.callback.encodeAsURL()
		         String token = requestToken.token + "&" + callbackParam
		         reqToken = new Token(token, requestToken.secret, requestToken.rawResponse)
	        } else {
		        reqToken = requestToken
	        }
        }

        session[oauthService.findSessionKeyForRequestToken(providerName)] = requestToken
        String url = oauthService.getAuthorizationUrl(providerName, reqToken)

        return redirect(url: url)
    }

}
