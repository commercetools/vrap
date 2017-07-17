package io.vrap

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.*


/**
 * Unit tests for {@link io.vrap.Validator.ValidationErrors}.
 */
class ValidationErrorsSpec extends Specification {

    def "serialize an ValidationErrors object with a response body"() {
        setup:
            def objectMapper = new ObjectMapper();
            def responseBody = '''{"name": "test1", "name": "test2"}'''
            def errors = new Validator.ValidationErrors([], 0, responseBody)
        when:
            def errorsAsJson = objectMapper.writeValueAsString(errors)
        then:
            errorsAsJson.contains(responseBody)
    }
}
