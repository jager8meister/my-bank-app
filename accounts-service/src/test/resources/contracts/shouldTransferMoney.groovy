package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should transfer money between accounts (internal microservice endpoint)"
    request {
        method POST()
        url("/api/accounts/internal/transfer") {
            headers {
                header('Authorization', 'Bearer test-token')
            }
            queryParameters {
                parameter('from', 'ivanov')
                parameter('to', 'petrov')
                parameter('amount', 500)
            }
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                senderBalance: 9500,
                recipientBalance: 10500,
                senderName: "Ivan Ivanov",
                recipientName: "Petr Petrov"
        ])
    }
}
