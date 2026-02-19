package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return account information for a valid login"
    request {
        method GET()
        url("/api/accounts/ivanov") {
            headers {
                header('Authorization', 'Bearer test-token')
            }
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                name: "Ivan Ivanov",
                birthdate: "1990-01-01",
                sum: 10000
        ])
    }
}
