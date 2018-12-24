if [ $# -eq 0 ]; then
  echo "Usage: initialize-config.sh <config service IP> "
  exit 0
fi


curl -X POST --data 'foo { bar { baz: 1000 }}' http://$1:5000/config/org/tmt/tcs/tcs_test.conf 



