const http = require('http');

(async () => {
    while (true) {
        try {
            await new Promise((resolve, reject) => {
                http.get('http://localhost:3000', resolve).on("error", reject);
            });
            return
        } catch (e) {
            await new Promise(resolve => {
                setTimeout(resolve, 1000);
            });
            console.log("wait debug server started");
        }
    }
})();