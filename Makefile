NAMESPACE    := bank
HELM_CHART   := helm/bank-app
HELM_RELEASE := bank-app

SERVICES := accounts-service auth-service cash-service transfer-service \
            notifications-service gateway-service front-service

HELM := $(shell which helm 2>/dev/null || \
  find /c/Users/danil/AppData/Local/Microsoft/WinGet -name "helm.exe" 2>/dev/null | head -1)

.PHONY: build deploy undeploy rebuild restart status clean check \
        $(addprefix build-,$(SERVICES)) \
        $(addprefix logs-,$(SERVICES))

check:
	@command -v kubectl >/dev/null 2>&1 || (echo "kubectl not found"; exit 1)
	@test -n "$(HELM)" || (echo "helm not found"; exit 1)
	@echo "kubectl: $$(kubectl version --client --short 2>/dev/null || kubectl version --client)"
	@echo "helm:    $$("$(HELM)" version --short)"

build: $(addprefix build-,$(SERVICES))

build-accounts-service:
	docker build -t my-bank-app-accounts-service:latest -f accounts-service/Dockerfile .

build-auth-service:
	docker build -t my-bank-app-auth-service:latest -f auth-service/Dockerfile .

build-cash-service:
	docker build -t my-bank-app-cash-service:latest -f cash-service/Dockerfile .

build-transfer-service:
	docker build -t my-bank-app-transfer-service:latest -f transfer-service/Dockerfile .

build-notifications-service:
	docker build -t my-bank-app-notifications-service:latest -f notifications-service/Dockerfile .

build-gateway-service:
	docker build -t my-bank-app-gateway-service:latest -f gateway-service/Dockerfile .

build-front-service:
	docker build -t my-bank-app-front-service:latest -f front-service/Dockerfile .

deploy:
	"$(HELM)" upgrade --install $(HELM_RELEASE) $(HELM_CHART) \
	  -n $(NAMESPACE) --create-namespace \
	  --wait --rollback-on-failure --timeout 10m
	kubectl rollout status \
	  deployment/accounts-service deployment/auth-service \
	  deployment/cash-service deployment/transfer-service \
	  deployment/notifications-service deployment/gateway-service \
	  deployment/front-service -n $(NAMESPACE)

undeploy:
	"$(HELM)" uninstall $(HELM_RELEASE) -n $(NAMESPACE) --ignore-not-found
	kubectl delete pvc --all -n $(NAMESPACE) --ignore-not-found

rebuild: undeploy build deploy

restart:
	kubectl rollout restart \
	  deployment/accounts-service deployment/auth-service \
	  deployment/cash-service deployment/transfer-service \
	  deployment/notifications-service deployment/gateway-service \
	  deployment/front-service deployment/keycloak -n $(NAMESPACE)

status:
	kubectl get all -n $(NAMESPACE)

clean: undeploy
	kubectl delete namespace $(NAMESPACE) --ignore-not-found

$(addprefix logs-,$(SERVICES)):
	kubectl logs -n $(NAMESPACE) deployment/$(subst logs-,,$@) --tail=100 -f
