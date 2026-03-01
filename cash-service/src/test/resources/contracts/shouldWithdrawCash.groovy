package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should withdraw cash from account"
    request {
        method POST()
        url("/api/cash/ivanov") {
            headers {
                header('Authorization', 'Bearer test-token')
                contentType(applicationJson())
            }
            body([
                    value: 1000,
                    action: "GET"
            ])
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                newBalance: 9000,
                errors: null,
                info: "Withdrawal successful"
        ])
    }
}
