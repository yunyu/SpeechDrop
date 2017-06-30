Vue.filter('formatDate', function (timestamp) {
    var date = new Date(timestamp);
    var hours = date.getHours();
    var minutes = date.getMinutes();
    var ampm = hours >= 12 ? 'pm' : 'am';
    hours = hours % 12;
    hours = hours ? hours : 12;
    minutes = minutes < 10 ? '0'+minutes : minutes;
    var strTime = hours + ':' + minutes + ' ' + ampm;
    return strTime;
});

var processFileList = function (newList) {
    var processed = [];
    for (var i = 0; i < newList.length; i++) {
        if (!newList[i]) continue;
        newList[i].url = mediaUrl + roomId + '/' + i + '/' + newList[i].name;
        newList[i].origPos = i;
        processed.unshift(newList[i]);
    }
    return processed;
};

var deleteFile = function(fileIndex) {
    var r = new XMLHttpRequest();
    r.open("POST", "/{% ROOM %}/delete", true);
    for (var i = 0; i < uploadedFiles.fileList.length; i++) {
        if (uploadedFiles.fileList[i].origPos == fileIndex) {
            uploadedFiles.fileList.splice(i, 1);
            break;
        }
    }
    r.onreadystatechange = function () {
        if (r.readyState != 4 || r.status != 200) return;
        processFileList(JSON.parse(r.responseText));
    };
    var data = "fileIndex=" + fileIndex + "&_csrf_token=" + csrf;
    r.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    r.send(data);
};

var uploadedFiles = new Vue({
    el: '#room-container',
    data: {
        fileList: processFileList(initialFiles)
    }
});

function setUploadText(dropzoneElement, text) {
    dropzoneElement.getElementsByTagName("p")[0].innerHTML = text;
}

function resetDropzone(dropzoneElement) {
    setUploadText(dropzoneElement, "Drag files here or click to upload");
    dropzoneElement.className = "dz-clickable";
}

var dropCard = new Dropzone("div#file-dropzone", { url: "/" + roomId + "/upload",
    addedfile: function (file) {},
    uploadprogress: function (file, progress, bytes) {
        setUploadText(dropCard.element, "Uploading (" + Math.floor(progress) + "%)");
    },
    success: function (file, successMsg) {
        resetDropzone(dropCard.element);
        setUploadText(dropCard.element, "Upload successful!");
        dropCard.element.className += " dropzone-success";
        uploadedFiles.fileList = processFileList(JSON.parse(successMsg));
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
        formData.append("_csrf_token", csrf);
    },
    createImageThumbnails: false,
    maxFilesize: 5,
    uploadMultiple: false,
    acceptedFiles: allowedMimes
});

var sock = io("https://sock.speechdrop.net/");
sock.on("connect", function () {
   // console.log("Connected");
   sock.emit("join", roomId);
   sock.on("update", function (data) {
       uploadedFiles.fileList = processFileList(JSON.parse(data));
   })
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

resetDropzone(dropCard.element);
