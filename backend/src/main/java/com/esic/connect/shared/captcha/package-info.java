/**
 * Protection anti-robot des formulaires exposés (EF-AUTH-011).
 *
 * <p>Port {@code CaptchaVerifier} avec deux adaptateurs : Cloudflare
 * Turnstile en production, adaptateur local sinon. Aucun module métier ne
 * connaît le fournisseur.
 */
package com.esic.connect.shared.captcha;
