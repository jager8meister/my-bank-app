package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should transfer money between accounts"
    request {
        method POST()
        url("/api/transfer") {
            headers {
                header('Authorization', 'Bearer test-token')
                contentType(applicationJson())
            }
            body([
                    senderLogin: "ivanov",
                    recipientLogin: "petrov",
                    amount: 500
            ])
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                success: true,
                message: "Transfer successful",
                senderBalance: 9500,
                recipientBalance: 10500
        ])
    }
}
