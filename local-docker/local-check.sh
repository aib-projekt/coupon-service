#set -x

BASE=http://localhost:8080/api/v1/coupons
_IP_PL=46.205.200.77

# Zbuduj i uruchom (4 instancje)
docker compose build app
docker compose up -d --scale app=4 --wait --wait-timeout 60

# Poczekaj na health
docker compose ps   # app powinien mieć status "healthy"

###
# Kupon z limitem użyć (idealny do testu concurrency)
curl -s -X POST $BASE -H 'Content-Type: application/json' \
  -d '{"code":"LIMIT10","maxUses":10,"country":"PL","perUserLimit":false}' | jq

# Kupon per-user (każdy user może użyć tylko raz)
curl -s -X POST $BASE -H 'Content-Type: application/json' \
  -d '{"code":"PERUSER","maxUses":100,"country":"PL","perUserLimit":true}' | jq

# Pozostałe 8 (różne kraje, limity)
for code in SALE20 PROMO5 FLASH50 VIP100 NEWUSER WEEKEND SUMMER BACK2S; do
  curl -s -X POST $BASE -H 'Content-Type: application/json' \
    -d "{\"code\":\"$code\",\"maxUses\":5,\"country\":\"PL\",\"perUserLimit\":false}" | jq .code
done

###
# Wyślij 15 requestów równolegle na kupon z limitem 10
for i in $(seq 1 15); do
  (echo """$(curl -s -X POST $BASE/LIMIT10/redeem \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: ${_IP_PL}" \
    -d '{"userId":null}')""") &
done
wait

# Sprawdź stan końcowy — powinno być currentUses=10
curl -s $BASE/LIMIT10 | jq '{currentUses, maxUses}'

###
# Ten sam user — drugi redeem powinien dostać 409 ALREADY_USED
curl -s -X POST $BASE/PERUSER/redeem \
  -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: ${_IP_PL}" \
  -d '{"userId":"user-1"}' | jq

curl -s -X POST $BASE/PERUSER/redeem \
  -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: ${_IP_PL}" \
  -d '{"userId":"user-1"}' #| jq   # → 409 ALREADY_USED

# Inny user — powinno przejść
curl -s -X POST $BASE/PERUSER/redeem \
  -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: ${_IP_PL}" \
  -d '{"userId":"user-2"}' | jq   # → 200 OK

###
# Utwórz nowy kupon z limitem 10
curl -s -X POST $BASE \
  -H 'Content-Type: application/json' \
  -d '{"code":"xLIMIT10","maxUses":10,"country":"PL","perUserLimit":false}' | jq

# Wyślij 40 równoległych requestów (ruch rozłożony przez nginx na 4 instancje)
for i in $(seq 1 40); do
  (echo """$(curl -s -X POST $BASE/xLIMIT10/redeem \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: ${_IP_PL}" \
    -d '{"userId":null}')""") &
done
wait

# Stan końcowy — MUSI być dokładnie 10, nie więcej
curl -s $BASE/xLIMIT10 | jq '{currentUses, maxUses}'

docker compose logs -f app

echo 'Hit [Enter] to shutdown app...' && read
docker compose down -v
