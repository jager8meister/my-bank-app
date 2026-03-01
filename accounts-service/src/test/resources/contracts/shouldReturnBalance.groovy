package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return account balance for a valid login"
    request {
        method GET()
        url("/api/accounts/ivanov/balance") {
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
        body(10000)
    }
}
