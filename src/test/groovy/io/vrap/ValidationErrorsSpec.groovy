package io.vrap

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.*


/**
 * Unit tests for {@link io.vrap.Validator.ValidationErrors}.
 */
class ValidationErrorsSpec extends Specification {

    def "serialize an ValidationErrors object with different response bodies"() {
        setup:
            def objectMapper = new ObjectMapper();
            def errors = new Validator.ValidationErrors([], 0, responseBody)
        when:
            def errorsAsJson = objectMapper.writeValueAsString(errors)
        then:
            errorsAsJson.contains(responseBody)
        where:
            responseBody << ['''{"name": "test1", "name": "test2"}''', '''["test1", "test2"]''']
    }
}
