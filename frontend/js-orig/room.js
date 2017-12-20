new Vue({
    el: '#room-container',
    data: {
        files: initialFiles
    },
    mounted: function () {
        var self = this;
        ga('send', 'event', 'Room', 'join', roomId);
        // Nasty workaround for https://github.com/vuejs/vue/issues/5800
        var pendingAdd = false;

        // Token refresh
        setInterval(function () {
            var r = new XMLHttpRequest();
            r.open("GET", "/" + roomId + "/index", true);
            r.send();
        }, 600000);

        var eb = new EventBus('/sock');
        eb.onopen = function () {
            eb.registerHandler("speechdrop.room." + roomId, function (e, m) {
                if (pendingAdd) {
                    pendingAdd = false;
                } else {
                    self.files = JSON.parse(m.body);
                }
            });
        };
        eb.enableReconnect(true);

        function setUploadText(dropzoneElement, text) {
            dropzoneElement.getElementsByTagName("p")[0].innerHTML = text;
        }

        function resetDropzone(dropzoneElement) {
            setUploadText(dropzoneElement, "Drag files here or click to upload");
            dropzoneElement.className = "dz-clickable";
        }

        self.$nextTick(function () {
            var dropCard = new Dropzone("div#file-dropzone", {
                url: "/" + roomId + "/upload",
                addedfile: function (file) {
                },
                uploadprogress: function (file, progress, bytes) {
                    setUploadText(dropCard.element, "Uploading (" + Math.floor(progress) + "%)");
                },
                success: function (file, successMsg) {
                    resetDropzone(dropCard.element);
                    setUploadText(dropCard.element, "Upload successful!");
                    dropCard.element.className += " dropzone-success";
                    // Avoid Vue bug: https://github.com/vuejs/vue/issues/5800
                    pendingAdd = true;
                    self.files = successMsg;
                    setTimeout(function () {
                        pendingAdd = false;
                    }, 300);
                    setTimeout(function () {
                        resetDropzone(dropCard.element);
                    }, 2000);
                },
                error: function (file, errorMsg) {
                    resetDropzone(dropCard.element);
                    setUploadText(dropCard.element, "Upload failed, please retry.");
                    dropCard.element.className += " dropzone-fail";
                    console.log(errorMsg);
                    setTimeout(function () {
                        resetDropzone(dropCard.element);
                    }, 2000);
                },
                sending: function (file, xhr, formData) {
                    formData.append("X-XSRF-TOKEN", self.getCsrfToken());
                    ga('send', 'event', 'Room', 'upload', roomId);
                },
                createImageThumbnails: false,
                maxFilesize: 5,
                uploadMultiple: false,
                acceptedFiles: allowedMimes
            });
            resetDropzone(dropCard.element);
        });
    },
    methods: {
        deleteFile: function (fileIndex) {
            var self = this;
            var r = new XMLHttpRequest();
            r.open("POST", "/" + roomId + "/delete", true);
            for (var i = 0; i < self.fileList.length; i++) {
                if (self.fileList[i].origPos === fileIndex) {
                    self.files.splice(i, 1);
                    break;
                }
            }
            r.onload = function () {
                self.files = JSON.parse(r.response);
            };
            var data = "fileIndex=" + fileIndex;
            r.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            r.setRequestHeader('X-XSRF-TOKEN', self.getCsrfToken());
            r.send(data);
            ga('send', 'event', 'Room', 'delete', roomId);
        },
        getCsrfToken: function () {
            return Cookies.get('XSRF-TOKEN');
        }
    },
    computed: {
        fileList: function () {
            function formatDate(timestamp) {
                var date = new Date(timestamp);
                var hours = date.getHours();
                var minutes = date.getMinutes();
                var ampm = hours >= 12 ? 'pm' : 'am';
                hours = hours % 12;
                hours = hours ? hours : 12;
                minutes = minutes < 10 ? '0' + minutes : minutes;
                return hours + ':' + minutes + ' ' + ampm;
            }

            var processed = [];
            for (var i = 0; i < this.files.length; i++) {
                var currEl = this.files[i];
                if (!currEl) continue;
                currEl.url = mediaUrl + roomId + '/' + i + '/' + currEl.name;
                currEl.origPos = i;
                currEl.formattedDate = formatDate(currEl.ctime);
                processed.unshift(currEl);
            }
            return processed;
        }
    }
});
