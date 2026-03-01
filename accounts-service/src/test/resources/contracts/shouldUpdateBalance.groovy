package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should update account balance"
    request {
        method PUT()
        url("/api/accounts/ivanov/balance") {
            headers {
                header('Authorization', 'Bearer test-token')
            }
            queryParameters {
                parameter('balance', 15000)
            }
        }
    }
    response {
        status 200
    }
}
