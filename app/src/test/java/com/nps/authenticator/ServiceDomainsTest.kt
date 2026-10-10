package com.nps.authenticator

import com.nps.authenticator.data.ServiceDomains
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceDomainsTest {
    @Test fun clean_normalizesInput() {
        assertEquals("netlify.com", ServiceDomains.clean("https://www.NetLify.com/login?x=1"))
        assertEquals("github.com", ServiceDomains.clean("  github.com "))
    }

    @Test fun clean_rejectsUnsafeInput() {
        assertEquals("", ServiceDomains.clean("localhost"))
        assertEquals("", ServiceDomains.clean("192.168.1.1"))
        assertEquals("", ServiceDomains.clean("exemple.com:8080"))
        assertEquals("", ServiceDomains.clean("a b.com"))
    }

    @Test fun guess_knownAndDomainLike() {
        assertEquals("google.com", ServiceDomains.guess("Google"))
        assertEquals("netlify.com", ServiceDomains.guess("Netlify"))
        assertEquals("exemple.org", ServiceDomains.guess("exemple.org"))
        assertEquals("", ServiceDomains.guess("Mon Service"))
    }
}
