package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should send notification to user"
    request {
        method POST()
        url("/api/notifications") {
            headers {
                header('Authorization', 'Bearer test-token')
                contentType(applicationJson())
            }
            body([
                    recipient: "ivanov",
                    message: "Your balance has been updated",
                    type: "ACCOUNT_UPDATED"
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
                message: "Notification sent successfully"
        ])
    }
}
