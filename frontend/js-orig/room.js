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

function processFileList(newList) {
    var processed = [];
    for (var i = 0; i < newList.length; i++) {
        var currEl = newList[i];
        if (!currEl) continue;
        currEl.url = mediaUrl + roomId + '/' + i + '/' + currEl.name;
        currEl.origPos = i;
        currEl.formattedDate = formatDate(currEl.ctime);
        processed.unshift(currEl);
    }
    return processed;
}

function getCsrfToken() {
    return Cookies.get('XSRF-TOKEN');
}

// Token refresh
setInterval(function () {
    var r = new XMLHttpRequest();
    r.open("GET", "/" + roomId + "/index", true);
    r.send();
}, 600000);

var uploadedFiles = new Vue({
    el: '#room-container',
    data: {
        fileList: processFileList(initialFiles)
    },
    mounted: function () {
        ga('send', 'event', 'Room', 'join', roomId);

        var eb = new EventBus('/sock', {vertxbus_reconnect_interval: 3000});
        eb.onopen = function () {
            eb.registerHandler("speechdrop.room." + roomId, function (e, m) {
                uploadedFiles.fileList = processFileList(JSON.parse(m.body));
            });
        };

        function setUploadText(dropzoneElement, text) {
            dropzoneElement.getElementsByTagName("p")[0].innerHTML = text;
        }

        function resetDropzone(dropzoneElement) {
            setUploadText(dropzoneElement, "Drag files here or click to upload");
            dropzoneElement.className = "dz-clickable";
        }

        this.$nextTick(function () {
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
                    uploadedFiles.fileList = processFileList(successMsg);
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
                    formData.append("X-XSRF-TOKEN", getCsrfToken());
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
            var r = new XMLHttpRequest();
            r.open("POST", "/" + roomId + "/delete", true);
            for (var i = 0; i < uploadedFiles.fileList.length; i++) {
                if (uploadedFiles.fileList[i].origPos === fileIndex) {
                    uploadedFiles.fileList.splice(i, 1);
                    break;
                }
            }
            r.onload = function () {
                processFileList(JSON.parse(r.response));
            };
            var data = "fileIndex=" + fileIndex;
            r.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            r.setRequestHeader('X-XSRF-TOKEN', getCsrfToken());
            r.send(data);
            ga('send', 'event', 'Room', 'delete', roomId);
        }
    }
});

/*
 var refreshCards = function () {
 var r = new XMLHttpRequest();
 r.open("GET", "/" + roomId + "/index?_=" + Date.now(), true);
 r.onload = function (e) {
 if (r.readyState === 4) {
 if (r.status === 200) {
 uploadedFiles.fileList = processFileList(JSON.parse(r.responseText));
 } else {
 console.error(r.statusText);
 }
 }
 };
 r.send(null);
 };

 refreshCards();
 setInterval(refreshCards, 3000);
 */
