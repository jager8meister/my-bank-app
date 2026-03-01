package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should deposit cash to account"
    request {
        method POST()
        url("/api/cash/ivanov") {
            headers {
                header('Authorization', 'Bearer test-token')
                contentType(applicationJson())
            }
            body([
                    value: 1000,
                    action: "PUT"
            ])
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                newBalance: 11000,
                errors: null,
                info: "Deposit successful"
        ])
    }
}
